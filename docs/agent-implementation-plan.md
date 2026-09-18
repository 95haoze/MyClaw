# AI Agent 实现完整方案

> 目标：实现一个**模型无关、可工具调用、带记忆、可观测、安全可控**的 Agent 运行时。
> 本文以 Java 为例，对应 MyClaw 的 `myclaw-core` 架构；设计思想与语言无关，可直接迁移到 TS/Python/Go。

---

## 1. 目标与设计原则

Agent 要解决的核心问题：**让大模型不仅能"生成文本"，还能"真正干活"**——查资料、算数、读写文件、多步推理。这需要一套围绕模型调用之外的配套机制。

设计原则（按优先级）：

1. **模型无关**：框架只依赖一个 `ChatModel` 抽象，任何厂商/自建模型实现该接口即可接入。
2. **错误不崩、可自愈**：工具异常、参数非法、JSON 损坏都不让 Agent 崩溃，而是转成 `ERROR:` 回填给模型自我纠正。
3. **状态与逻辑分离**：`Agent` 无状态、可并发共享；会话状态全部放在 `Memory`。
4. **安全边界内收敛**：文件系统沙箱、Shell 默认关闭、无脚本引擎。
5. **可观测但不干扰**：钩子覆盖全生命周期，钩子异常不影响主流程。

---

## 2. 总体架构

```
用户输入
   │
   ▼
┌─────────────────────────────────────────────────────────┐
│  Agent  （ReAct 循环，无状态）                            │
│                                                         │
│  1. 组装请求 = [system] + Memory + 工具定义               │
│  2. 调用 ChatModel                                       │
│  3. 响应里有 tool_calls？                                 │
│       ├─ 是 → ToolRegistry.executeAll → 结果写回 Memory  │
│       │        └─ 回到 1（下一轮）                        │
│       └─ 否 → 产出最终答案，结束                          │
│                                                         │
│  依赖：ChatModel（模型）· ToolRegistry（手）· Memory（脑） │
│  穿插：AgentHook（可观测）                                │
└─────────────────────────────────────────────────────────┘
```

模块分层：

| 层 | 职责 | 关键类型 |
|---|---|---|
| 消息层 | 唯一的数据交换单位 | `Role` / `Message` / `ToolCall` / `ChatUsage` |
| 模型层 | 唯一与厂商耦合的接口 | `ChatModel` / `ChatRequest` / `ChatResponse` / `ChatOptions` / `StreamListener` |
| 工具层 | Agent 的"手" | `Tool` / `ToolDefinition` / `ToolRegistry` / `ToolContext` / `ToolResult` / `JsonSchema` |
| 记忆层 | Agent 的短期记忆 | `Memory` / `InMemoryMemory`（滑动窗口） |
| 循环层 | 编排 ReAct 循环 | `Agent` / `AgentRequest` / `AgentResponse` / `AgentStep` / `AgentHook` |
| 技能层 | 按需加载的指令包 | `Skill` / `SkillRegistry` / `list_skills` / `load_skill` |

---

## 3. 核心抽象设计

### 3.1 Message —— 唯一交换单位

```java
public record Message(Role role, String content, List<ToolCall> toolCalls,
                      String toolCallId, String name) {

    static Message system(String content);
    static Message user(String content);
    static Message assistant(String content);
    static Message assistant(String content, List<ToolCall> toolCalls);
    static Message tool(String toolCallId, String name, String content);
}
```

四种角色使用不同字段：

| Role | 使用字段 |
|---|---|
| `system` / `user` | 仅 `content` |
| `assistant` | `content`（可空）+ `toolCalls` |
| `tool` | `toolCallId` + `name` + `content`（工具结果） |

`ToolCall` 含 `id`（唯一标识，用于把结果回挂到对应调用）、`name`、`arguments`（JSON 字符串）。

### 3.2 ChatModel —— 唯一与厂商耦合的接口

```java
public interface ChatModel {
    ChatResponse chat(ChatRequest request);              // 必实现，同步补全
    default ChatResponse stream(ChatRequest r, StreamListener l); // 有默认实现（退化为一次性输出）
    default String name();
    default boolean supportsToolCalling() { return true; } // false 时 Agent 不暴露工具
}
```

关键点：`stream` 提供默认实现（内部调 `chat` 一次性吐出），因此不支持流式的厂商零成本接入。异常统一包装为 `ModelException`。

`ChatRequest` 携带：`messages` + `tools`（工具定义列表）+ `options`（temperature/maxTokens/厂商私有参数）。

### 3.3 Tool —— 三件事就够了

```java
public interface Tool {
    ToolDefinition definition();                       // 自描述（名字/描述/JSON Schema）
    String call(JsonNode arguments, ToolContext context) throws Exception; // 执行
    default String name();
    default boolean enabled() { return true; }
}
```

- **`definition()`** 里的 `description` 是模型判断"何时调用该工具"的主要依据，必须写清触发场景。
- 工具必须**无状态、线程安全**：本次调用的所有信息（工作目录、Agent 名、自定义属性）经 `ToolContext` 传入。
- `call` 抛出的异常由 `ToolRegistry` 捕获并转成错误结果，工具作者无需自己 try/catch。

`ToolDefinition`：`name` + `description` + `parameters`（`JsonSchema`，支持 object/string/number/boolean/enum/required）。

### 3.4 ToolRegistry —— 兜底容错执行器

```java
public class ToolRegistry {
    ToolRegistry register(Tool tool);
    List<ToolDefinition> definitions();                 // 导出给模型
    ToolResult execute(ToolCall call, ToolContext ctx); // 单次执行
    List<ToolResult> executeAll(List<ToolCall> calls, ToolContext ctx);
}
```

`execute` 的容错逻辑（核心）：
1. 工具不存在 → 返回 `失败(tool_call_id, name, "工具 `x` 不存在。可用工具: [...]")`
2. 参数 JSON 解析失败 → 返回错误结果
3. 工具抛异常 → 捕获，取异常 message，返回失败结果
4. 成功 → 返回 `成功(id, name, content, elapsedMs)`

所有失败统一以 `ERROR: ...` 形式回填给模型，模型即可据此换参数/换工具/承认失败。

### 3.5 ToolContext —— 运行时环境唯一通道

```java
public final class ToolContext {
    String agentName();
    Path workingDirectory();
    Map<String,Object> attributes();
    Path resolve(String raw);   // 关键：解析路径并强制约束在工作目录内
}
```

`resolve` 的安全语义：相对路径基于 `workingDirectory` 解析，绝对路径直接取，然后 `normalize()` 后校验 `startsWith(workingDirectory)`，否则抛 `SecurityException`。所有文件类工具必须走 `resolve`，从根上杜绝 `../` 穿越与绝对路径逃逸。

### 3.6 Memory —— 短期记忆

```java
public interface Memory {
    List<Message> messages();
    void add(Message message);
    void clear();
    int size();
}
```

`InMemoryMemory(maxMessages)` 是滑动窗口实现，**裁剪时做结构修复**：若窗口头部残留 `tool` 消息（其对应的 `assistant(tool_calls)` 已被裁掉），则一并丢弃——否则违反 OpenAI 协议，厂商直接返回 400。

### 3.7 AgentHook —— 可观测插入点

```java
public interface AgentHook {
    default void onAgentStart(Agent a, AgentRequest r) {}
    default void onModelRequest(Agent a, ChatRequest r) {}
    default void onModelResponse(Agent a, ChatResponse r) {}
    default void onToolCall(Agent a, ToolCall c, ToolResult r) {}
    default void onAgentEnd(Agent a, AgentResponse r) {}
    default void onError(Agent a, Throwable e) {}
}
```

钩子异常被框架捕获并记日志，**不影响主流程**。典型用途：日志、审计、链路追踪、成本统计。

### 3.8 Skill —— 按需加载的指令包

技能平时只以「名称 + 一句话描述」暴露（几乎不占 token），模型调用 `load_skill` 时才把完整正文注入上下文。用不上的技能一个 token 都不占——这是最省 token 的扩展方式。

```
skills/
└── token-budget/
    └── SKILL.md    # YAML frontmatter(name/description) + Markdown 正文
```

---

## 4. 核心流程：ReAct 循环

```java
public AgentResponse run(AgentRequest request) {
    memory.add(Message.user(request.input()));          // 写入用户输入
    fireHooks(h -> h.onAgentStart(this, request));

    int iteration = 0;
    while (true) {
        if (cancelled) throw new CancellationException();
        if (++iteration > maxIterations) throw new MaxIterationsException(maxIterations);

        // 1. 组装请求：system + 记忆 + 工具定义
        ChatRequest chatRequest = buildChatRequest(request);   // [system] + memory.messages() + tools.definitions()
        fireHooks(h -> h.onModelRequest(this, chatRequest));

        // 2. 调用模型（流式或非流式）
        ChatResponse response = invoke(chatRequest, request);
        fireHooks(h -> h.onModelResponse(this, response));
        totalUsage = totalUsage.plus(response.usage());

        Message assistant = response.message();
        memory.add(assistant);                          // 模型回复也入记忆

        List<ToolCall> toolCalls = assistant.toolCalls();
        if (toolCalls.isEmpty()) {                      // 3a. 无工具调用 → 最终答案
            return new AgentResponse(answer, steps, totalUsage, iteration, elapsed);
        }

        // 3b. 有工具调用 → 执行 → 结果写回记忆 → 下一轮
        List<ToolResult> results = tools.executeAll(toolCalls, context);
        for (ToolResult r : results)
            memory.add(Message.tool(r.toolCallId(), r.toolName(), r.toModelContent()));
        // 逐条触发 onToolCall 钩子，记录 AgentStep.ToolUse
    }
}
```

终止条件有且仅有三种：
1. 模型返回**不含 tool_calls** 的消息 → 正常结束；
2. 达到 `maxIterations` → 抛 `MaxIterationsException`（防死循环）；
3. 流式监听器发出取消 → 抛 `CancellationException`。

---

## 5. 关键设计点

### 5.1 错误兜底（不崩 + 可自愈）

- 工具不存在 / 参数 JSON 损坏 / 工具抛异常，**全部**转成 `ERROR:` 结果回填，而非抛出。
- 模型看到错误后可以：修正参数重试、换一个工具、或直接承认无法完成。
- 系统提示词里明确写入"工具返回 ERROR 时先读懂原因再重试"，实测能显著降低小模型的乱调用率。

### 5.2 死循环保护

`maxIterations` 是**模型调用轮数**上限（默认 10），到顶抛异常而不是无限烧 token。这是 token 预算的最后一道防线。

### 5.3 记忆配对修复

滑动窗口裁剪时，若切点落在 `tool` 消息上（对应的 `assistant(tool_calls)` 被裁掉），继续向后跳过这些 `tool` 消息，保证 `assistant(tool_calls)` 与其 `tool` 结果始终成对出现。

### 5.4 流式与 tool_calls 分片聚合

- 文本增量经 `StreamListener.onText` 实时回调。
- 流式返回的 tool_calls 是分片到达的，需按 `index` 把 `id`/`name`/`arguments` 聚合为完整 `ToolCall`，否则会得到残缺参数。

### 5.5 自动重试

- 429 / 5xx 指数退避重试（上限 `maxRetries`）。
- 流式仅在**尚未输出任何内容**时重试（一旦有内容落地就无法安全重放）。

### 5.6 安全边界

1. 文件工具强制走 `ToolContext.resolve`，越界抛 `SecurityException`；
2. Shell 工具默认关闭，可选命令前缀白名单 + 高危片段拦截；
3. 计算工具用自研解析器，无脚本引擎/反射求值；
4. 日志对 API Key 脱敏（`sk-abc****`）。

---

## 6. 模型接入（OpenAI 兼容协议）

只实现 `ChatModel` 一个接口。OpenAI 兼容协议实现要点：

1. **请求编解码**：`Message`/`ToolDefinition` → OpenAI JSON（`messages` + `tools` + 采样参数）；
2. **响应编解码**：`choices[0].message` → `Message`，`tool_calls` → `List<ToolCall>`，`usage` → `ChatUsage`；
3. **流式**：解析 SSE `data:` 行，`delta` 增量聚合；
4. **错误解析**：非 2xx 时提取厂商错误信息，包装成 `ModelException`；
5. **多厂商**：同一套协议 + 不同 endpoint/key/model 预设（OpenAI / 通义千问 / DeepSeek / Ollama）。

---

## 7. 工具开发两种方式

**方式一：实现 `Tool` 接口**（适合动态 Schema、非 Spring 场景）：

```java
public class WeatherTool implements Tool {
    public ToolDefinition definition() {
        return ToolDefinition.builder("get_weather")
                .description("查询指定城市实时天气。用户问天气/气温/下雨时使用。")
                .parameters(JsonSchema.object()
                        .string("city", "城市名，如 杭州")
                        .enumOf("unit", "温度单位", List.of("celsius","fahrenheit"), false)
                        .build())
                .build();
    }
    public String call(JsonNode args, ToolContext ctx) {
        return "杭州 晴 26°C";
    }
}
```

**方式二：注解（推荐，Spring 场景）**：

```java
@MyClawTool(name = "get_weather", description = "...")
public String getWeather(@MyClawParam("city") String city,
                         @MyClawParam(value = "unit", required = false) Unit unit) {
    return "杭州 晴 26°C";
}
```

框架自动：从方法签名+注解生成 JSON Schema → 参数类型转换/校验 → 反射调用 → 返回值转字符串。

---

## 8. 落地步骤（里程碑）

| 阶段 | 交付物 | 验收标准 |
|---|---|---|
| M1 内核骨架 | `Message`/`ChatModel`/`ChatRequest`/`ChatResponse` | 编译通过，单测覆盖消息构造 |
| M2 工具系统 | `Tool`/`ToolDefinition`/`ToolRegistry`/`ToolContext`/`JsonSchema` | 工具注册、执行、异常兜底全测通 |
| M3 记忆 | `Memory`/`InMemoryMemory` | 滑动窗口 + 配对修复通过测试 |
| M4 Agent 循环 | `Agent`/`AgentRequest`/`AgentResponse`/`AgentStep` | 用脚本化假模型跑通多轮工具往返、死循环保护、流式 |
| M5 模型接入 | `OpenAiChatModel` + 协议编解码 + SSE | 本地 HttpServer 打桩，端到端 + 重试测试 |
| M6 可观测 | `AgentHook`/`LoggingHook` | 全生命周期埋点、钩子异常不中断 |
| M7 内置工具 | 计算器/时间/文件读写列/HTTP/Shell | 沙箱越界、白名单、HTML 转文本测试 |
| M8 技能 | `Skill`/`SkillRegistry`/`list_skills`/`load_skill` | SKILL.md 扫描与按需加载 |
| M9 接入层 | CLI + Spring Boot starter + Web | 零配置跑通完整工具链路 |

---

## 9. 测试策略

全部测试**离线、确定性**，不依赖网络与 API Key：

- **假模型**：`ScriptedChatModel` 按脚本返回预设的 tool_calls/文本，用于验证 Agent 循环。
- **假模型 + 记录器工具**：验证参数传递、结果回填、多轮顺序。
- **本地 HTTP 桩**：JDK `com.sun.net.httpserver` 起桩，覆盖请求构造、鉴权头、SSE 分片聚合、429/5xx 重试、错误解析。
- **安全测试**：`ToolContext.resolve` 的 `../` 穿越、绝对路径、越界。

---

## 10. 配置与扩展点

```yaml
myclaw:
  api-key: ${DASHSCOPE_API_KEY}
  model: qwen-plus
  temperature: 0.7
  max-tokens: ~
  timeout: 120s
  max-retries: 2
  max-iterations: 10        # 防死循环
  working-directory: .      # 沙箱根
  memory:
    max-messages: 40        # 0 = 不限制
  tools:
    calculator: true
    file-read: true
    file-write: false       # 建议默认关
    shell: false            # 默认关
    shell-allowlist: []
  skills:
    enabled: true
    directories: [skills]
```

扩展点一览：

| 想做的事 | 做法 |
|---|---|
| 接入私有模型 | 实现 `ChatModel` |
| 自定义记忆存储（Redis/DB） | 实现 `Memory` |
| 埋点/审计/链路追踪 | 实现 `AgentHook`（可注册多个） |
| 动态 Schema 工具 | 实现 `Tool`，`definition()` 里自由构造 |
| 替换默认配置 | 定义自己的 `Agent`/`ToolRegistry`/`ChatModel` Bean |

---

## 11. 最小可用代码骨架

```java
// 1. 组装工具
ToolRegistry tools = new ToolRegistry()
        .register(new CalculatorTool())
        .register(new HttpFetchTool())
        .register(new FileReadTool());

// 2. 接模型
ChatModel model = new OpenAiChatModel(OpenAiConfig.dashscope(apiKey).build());

// 3. 构建 Agent
Agent agent = Agent.builder("assistant")
        .model(model)
        .tools(tools)
        .systemPrompt(AgentPrompts.DEFAULT)
        .maxIterations(8)
        .hook(new LoggingHook())
        .build();

// 4. 运行
AgentResponse r = agent.run("算一下 1234*5678，再去查一下今天的汇率");
System.out.println(r.content());   // 最终答案
System.out.println(r.usedTools()); // 是否用过工具
System.out.println(r.usage());     // token 统计

// 5. 多会话隔离（Agent 无状态，Memory 有状态）
Agent session = agent.withMemory(new InMemoryMemory(40));
```

---

## 12. 一页纸总结

**Agent = ReAct 循环 + 模型抽象 + 工具系统 + 记忆 + 可观测 + 安全边界。**

- 循环靠 `Agent`，模型靠 `ChatModel`，干活靠 `Tool`，记性靠 `Memory`，埋点靠 `AgentHook`。
- 六个关键工程点：错误兜底、死循环保护、记忆配对修复、流式分片聚合、指数退避重试、沙箱安全。
- 一条铁律：**工具结果回填，异常转 ERROR，绝不编造、绝不崩溃。**
