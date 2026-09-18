# MyClaw

> 一个轻量、模型无关的 Java **AI Agent 框架**。用一套 OpenAI 兼容协议对接所有主流模型，内置 ReAct 循环、工具系统、记忆与可观测钩子。

技术栈：**Java 25 · Spring Boot 4.0.1 · Maven 多模块 · Jackson 3**

---

## 目录

- [它解决什么问题](#它解决什么问题)
- [核心特性](#核心特性)
- [模块结构](#模块结构)
- [快速开始](#快速开始)
- [核心概念](#核心概念)
- [自定义工具](#自定义工具)
- [技能：按需加载的指令](#技能按需加载的指令)
- [降低 token 消耗](#降低-token-消耗)
- [配置项](#配置项)
- [对接不同模型](#对接不同模型)
- [安全边界](#安全边界)
- [测试](#测试)
- [扩展点](#扩展点)
- [已知环境问题](#已知环境问题)

---

## 它解决什么问题

直接调用大模型 API 只解决了"生成文本"，但要让它**真正干活**——查资料、算数、读写文件、多步推理——需要一整套配套机制：工具注册与调用、多轮循环控制、错误回滚给模型、上下文管理、可观测性。

MyClaw 把这套机制沉淀成框架：

```
用户提问 ──► Agent ──► 模型 ──► 要调用工具吗？
                            │         │
                            │         ├─ 是 ──► 执行工具 ──► 结果回填 ──┐
                            │         │                                │
                            │         └─ 否 ──► 最终答案              │
                            └──────────────────────────────────────────┘
```

框架负责循环、工具、容错、记忆；你只需要写提示词和业务工具。

## 核心特性

| 能力 | 说明 |
|---|---|
| **ReAct Agent 循环** | 自动完成"推理 → 调工具 → 观察结果 → 再推理"，带最大轮数保护，防止死循环 |
| **模型无关** | 只依赖一个 `ChatModel` 接口。内置 OpenAI 兼容实现，一套代码通吃 OpenAI / 通义千问 / DeepSeek / Ollama |
| **工具系统** | 实现 `Tool` 接口，或用 `@MyClawTool` 注解，方法自动变成工具（含 JSON Schema 生成与参数类型转换） |
| **兜底容错** | 工具抛异常、参数非法、工具不存在、参数 JSON 损坏——全部转成 `ERROR:` 结果回填给模型，让它自己纠正，而不是让 Agent 崩掉 |
| **真流式输出** | SSE 增量回调；流式返回的工具调用分片按 index 自动聚合 |
| **滑动窗口记忆** | 自动裁剪历史，并保证 `assistant(tool_calls)` 与 `tool` 结果的配对不被切断 |
| **可观测钩子** | `AgentHook` 覆盖 Agent / 模型 / 工具全生命周期，钩子自身异常不影响主流程 |
| **安全边界** | 所有文件工具强制沙箱在工作目录内；Shell 工具默认关闭 |
| **自动重试** | 429 / 5xx 指数退避重试；流式仅在尚未输出内容时重试 |

## 模块结构

```
MyClaw
├── myclaw-core                   内核：与厂商、框架完全无关的抽象
│   ├── message/                  Role / Message / ToolCall / ChatUsage
│   ├── model/                    ChatModel / ChatRequest / ChatResponse / ChatOptions / StreamListener
│   ├── tool/                     Tool / ToolDefinition / ToolRegistry / ToolContext / JsonSchema
│   ├── agent/                    Agent（ReAct 循环）/ AgentHook / AgentStep / AgentPrompts
│   ├── memory/                   Memory / InMemoryMemory（滑动窗口 + 配对修复）
│   ├── skill/                    Skill / SkillRegistry（扫描 SKILL.md）/ list_skills + load_skill
│   ├── json/                     Json（Jackson 3 门面）
│   └── exception/                MyClawException / ModelException / MaxIterationsException
│
├── myclaw-openai                  OpenAI Chat Completions 兼容协议实现（纯 JDK HttpClient）
│   ├── OpenAiConfig              端点 / Key / 模型 / 超时 / 重试 + 各厂商预设 + 环境变量推断
│   ├── OpenAiProtocol            协议编解码（消息、工具、usage、错误信息）
│   └── OpenAiChatModel           同步调用 + SSE 流式 + 重试
│
├── myclaw-tools                   内置工具集
│   ├── CalculatorTool            calculate      安全的自研表达式求值器（无脚本引擎）
│   ├── CurrentTimeTool           current_time   时区 + 自定义格式
│   ├── FileReadTool              read_file      带行号、分页
│   ├── FileWriteTool             write_file     自动建目录
│   ├── FileListTool              list_files     递归 / 非递归
│   ├── HttpFetchTool             http_fetch     HTML 去标签转纯文本
│   ├── ShellTool                 run_command    默认禁用 + 白名单 + 高危拦截
│   └── BuiltinTools              工厂：all() / safe() / registry(...)
│
├── myclaw-spring-boot-starter     Spring Boot 4 自动配置
│   ├── MyClawProperties          myclaw.* 配置绑定
│   ├── MyClawAutoConfiguration   零配置产出 ChatModel / ToolRegistry / Agent
│   ├── MyClawToolRegistrar       扫描 @MyClawTool 方法并注册
│   ├── tool/MethodTool           方法 -> Tool 适配器（Schema 生成 + 参数转换）
│   └── annotation/               @MyClawTool / @MyClawParam / @MyClawSkill
│
└── myclaw-cli                 可运行示例（命令行 Agent）
```

## 快速开始

### 环境要求

- JDK 25+（框架代码使用 record / sealed / pattern matching / text block）
- Maven 3.9+

### 1. 构建

```bash
mvn clean install
```

### 2. 零配置跑起来

```bash
# 不需要 API Key：用一个离线演示模型跑通完整的工具调用链路
java -jar myclaw-cli/target/myclaw-cli-1.0.0.jar \
     --spring.profiles.active=offline \
     --prompt="算一下 1234 * 5678"
```

输出：

```
我先用 calculate 工具算一下：1234 * 5678
（离线演示模型）calculate 工具返回的结果是：7006652
[2 轮模型调用 | 1 次工具 | 0 tokens | 54 ms]
```

### 3. 接真实模型

设置任意一个环境变量即可，框架会自动推断端点与默认模型：

```bash
export DASHSCOPE_API_KEY=sk-xxxx      # 通义千问
# 或 export OPENAI_API_KEY=sk-xxxx    # OpenAI
# 或 export MYCLAW_API_KEY=sk-xxxx    # 任意 OpenAI 兼容端点（配合 MYCLAW_BASE_URL）
```

```bash
# 交互式对话
java -jar myclaw-cli/target/myclaw-cli-1.0.0.jar

# 单次提问
java -jar myclaw-cli/target/myclaw-cli-1.0.0.jar \
     --prompt="杭州现在几点？顺便算一下 98765 乘以 43210"
```

```
============================================================
  MyClaw 示例 Agent
------------------------------------------------------------
  模型 : openai:qwen-plus
  工具 : [calculate, current_time, http_fetch, list_files, read_file, ...]
------------------------------------------------------------
  直接输入问题回车；/reset 清空记忆；exit 退出
============================================================

你 > 杭州现在几点？
现在是 2026-09-10 星期四 14:48:41（Asia/Shanghai）。
[2 轮模型调用 | 1 次工具 | 412 tokens | 1832 ms]
```

### 4. 在 Spring Boot 项目里用

```xml
<dependency>
    <groupId>io.myclaw</groupId>
    <artifactId>myclaw-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
myclaw:
  api-key: ${DASHSCOPE_API_KEY}
  temperature: 0.3
```

```java
@RestController
public class ChatController {

    private final Agent agent;

    public ChatController(Agent agent) {
        this.agent = agent;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String q) {
        return agent.run(q).content();
    }
}
```

## 核心概念

### Message —— 唯一的交换单位

```java
Message.system("你是助手");
Message.user("杭州天气怎么样？");
Message.assistant("我来查一下", List.of(new ToolCall("call_1", "get_weather", "{\"city\":\"杭州\"}")));
Message.tool("call_1", "get_weather", "晴 26℃");
```

### ChatModel —— 唯一与厂商耦合的接口

```java
public interface ChatModel {
    ChatResponse chat(ChatRequest request);
    default ChatResponse stream(ChatRequest request, StreamListener listener) { ... }
    default boolean supportsToolCalling() { return true; }
}
```

实现这一个接口就能接入任何模型。`stream` 有默认实现（退化为一次性输出），所以不支持流式的厂商也无需额外工作。

### Tool —— 三件事就够了

```java
public class WeatherTool implements Tool {

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("get_weather")
                .description("查询指定城市的实时天气。当用户询问天气时使用。")
                .parameters(JsonSchema.object()
                        .string("city", "城市名称，例如 杭州")
                        .enumOf("unit", "温度单位", List.of("celsius", "fahrenheit"), false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) {
        return "杭州 晴 26°C";
    }
}
```

工具应当**无状态、线程安全**——所有本次调用的信息通过 `ToolContext` 传入，因此同一个工具实例可以被多个 Agent、多个会话并发复用。

### Agent —— ReAct 循环

```java
Agent agent = Agent.builder("assistant")
        .model(new OpenAiChatModel(OpenAiConfig.dashscope(apiKey).build()))
        .tools(registry)
        .systemPrompt(AgentPrompts.DEFAULT)
        .maxIterations(8)
        .hook(new LoggingHook())
        .build();

AgentResponse response = agent.run("杭州今天适合出门吗？");

response.content();          // 最终答案
response.usedTools();        // 是否用过工具
response.toolResults();      // 每一次工具调用的结果
response.iterations();       // 经历了几轮模型调用
response.usage();            // 累计 token 消耗

// 流式
agent.run("讲个故事", StreamListener.printing(System.out));
```

`Agent` 实例本身无状态（状态都在 `Memory` 里），可安全共享；需要隔离会话时派生新实例：

```java
Agent session = agent.withMemory(new InMemoryMemory());
```

## 自定义工具

### 方式一：`@MyClawTool` 注解（推荐）

```java
@Component
public class WeatherTools {

    public enum Unit { CELSIUS, FAHRENHEIT }

    @MyClawTool(name = "get_weather",
            description = "查询指定城市的实时天气。当用户询问天气、气温、是否下雨时使用。")
    public String getWeather(
            @MyClawParam(value = "city", description = "城市名称，例如 杭州") String city,
            @MyClawParam(value = "unit", description = "温度单位", required = false) Unit unit) {
        return "杭州 晴 26°C";
    }
}
```

框架会自动：

- 从方法签名 + 注解生成 JSON Schema（含枚举约束、必填项）
- 把模型给的 JSON 参数按类型转换并校验（`String` / 基本类型 / `BigDecimal` / `enum` / `Path`）
- 反射调用，把返回值转成回填给模型的字符串
- 在 Spring 容器启动后自动注册，无需任何胶水代码

> **提示**：`description` 是模型决定是否调用该工具的最主要依据，务必写清楚"什么时候该用它"。留空时框架会打警告日志。

### 方式二：实现 `Tool` 接口

适合需要动态 Schema、或不属于任何 Spring Bean 的工具。

## 技能：按需加载的指令

技能是**按需加载的指令包**：模型平时只看得到「技能名 + 一句话描述」，只有调用 `load_skill` 时才把完整正文放进上下文。用不上的技能一个 token 都不占——这本身就是最省 token 的扩展方式。

```
skills/                       # myclaw.skills.directories 指向的目录
└── token-budget/
    └── SKILL.md              # Claude 风格：YAML frontmatter + Markdown 正文
```

```markdown
---
name: token-budget
description: "一句话说清什么时候该用，模型据此决定要不要加载"
---

# 正文：具体怎么做
```

| 注册方式 | 用法 | 适合 |
|---|---|---|
| `SKILL.md` 目录 | 放到 `skills/<技能名>/SKILL.md`，随文档一起交付 | 纯提示词类技能 |
| `@MyClawSkill` | 标注在 **public、无参、返回 `String`** 的方法上，`name` 缺省用方法名 | 指令由代码动态生成 |
| `Skill` Bean | 实现 `io.myclaw.core.skill.Skill` | 需要自定义 `enabled()` 等行为 |

模型侧只会多出两个工具：`list_skills`（返回名称与描述的 JSON）和 `load_skill`（返回某个技能的完整正文）。把 `myclaw.skills.enabled` 设为 `false` 即可整体关闭。

## 降低 token 消耗

每次模型调用都要**重新发送**系统提示词、全部工具的 JSON Schema、记忆窗口里的历史，以及本轮新增的工具结果，所以总消耗约等于：

```
(固定开销 × 轮数) + 累计工具结果 + 累计历史
```

优先级排序是：**减少轮数 > 缩小工具结果 > 把回答写短**。框架侧可以直接调的旋钮：

| 旋钮 | 作用 |
|---|---|
| `myclaw.memory.max-messages` | 限制历史条数（裁断时会自动修复 `assistant(tool_calls)` 与 `tool` 的配对） |
| `myclaw.max-iterations` | 兜底，防止工具调用死循环持续烧 token |
| `myclaw.max-tokens` | 限制单次输出长度 |
| `myclaw.tools.*` | 只开启真正需要的工具：每个工具的 Schema 每轮都会重发 |
| `myclaw.skills.*` | 技能按需加载，正文不进常驻上下文 |
| `read_file` 的 `startLine` / `maxLines` | 分页取用，避免整个文件（默认 500 行）进入上下文 |

除了配置，仓库还自带了 [`skills/token-budget/SKILL.md`](skills/token-budget/SKILL.md)：它把上面这些做法写成一套模型可执行的协议——先列目录再读文件、结果进上下文前先压缩、同一行段不读第二遍、够了就停——模型 `load_skill` 之后会照它执行。

## 配置项

```yaml
myclaw:
  enabled: true                          # 是否启用自动配置
  api-key: ${DASHSCOPE_API_KEY}          # 留空则从环境变量推断
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-plus                       # 留空则按 Key 来源推断
  temperature: 0.7
  max-tokens:                            # 留空交给服务端默认
  timeout: 120s
  max-retries: 2
  system-prompt: |                       # 留空使用 AgentPrompts.DEFAULT
    你是一个严谨的助手...
  max-iterations: 10                     # 最大模型调用轮数
  working-directory: .                   # 工具的沙箱根目录
  logging-hook: true
  memory:
    max-messages: 40                     # 0 表示不限制
  skills:
    enabled: true                        # 关闭后不再注册 list_skills / load_skill
    directories:                         # 递归扫描 <目录>/**/SKILL.md，相对进程工作目录
      - skills
  tools:
    builtins-enabled: true
    calculator: true
    current-time: true
    file-read: true
    file-write: false                    # 建议默认关闭
    file-list: true
    http-fetch: true
    shell: false                         # 默认关闭，开启前请评估风险
    shell-allowlist: []                  # 命令前缀白名单，空表示不限前缀
    shell-timeout: 30s
```

所有自动配置的 Bean 都带 `@ConditionalOnMissingBean`，你可以随时用自己的同类型 Bean 覆盖任意一环：

```java
@Bean
public ChatModel myModel() {
    return new MyCompanyChatModel();     // 覆盖默认的 OpenAiChatModel
}
```

## 对接不同模型

```java
// 通义千问
OpenAiConfig.dashscope(apiKey).build();

// DeepSeek
OpenAiConfig.deepseek(apiKey).build();

// 本地 Ollama
OpenAiConfig.ollama().build();                                  // http://localhost:11434/v1
OpenAiConfig.ollama("http://192.168.1.10:11434/v1", "qwen2.5:7b").build();

// 任意 OpenAI 兼容端点
OpenAiConfig.builder()
        .baseUrl("https://your-gateway/v1")
        .apiKey(key)
        .model("your-model")
        .header("X-Custom", "value")
        .build();

// 厂商私有参数透传
agent.run(AgentRequest.builder()
        .input("...")
        .options(ChatOptions.builder().extra("enable_search", true).build())
        .build());
```

## 安全边界

让模型自主调用工具意味着把一部分控制权交给了它，框架在几个关键位置做了收口：

1. **文件系统沙箱**：所有文件类工具必须通过 `ToolContext.resolve(path)` 解析路径，越出工作目录会直接抛 `SecurityException`——包括 `../` 穿越和指向外部的绝对路径。
2. **Shell 默认关闭**：`run_command` 只有在显式配置 `myclaw.tools.shell=true` 时才会注册，且支持命令前缀白名单与高危片段拦截。
3. **无脚本引擎**：`calculate` 使用自研的递归下降解析器，不调用任何脚本引擎或反射求值。
4. **循环保护**：`maxIterations` 到顶抛 `MaxIterationsException`，避免模型陷入工具调用死循环烧 token。
5. **失败不崩**：任何工具异常都被转成 `ERROR: ...` 回填给模型，让它有机会自我纠正；`AgentHook` 抛异常也不会影响主流程。
6. **Key 脱敏**：日志里只输出 `sk-abc****`，不打印完整 Key。

## 测试

```bash
mvn clean test
```

共 **172** 个测试，全部离线、确定性执行，不依赖网络与 API Key：

| 模块 | 测试数 | 覆盖内容 |
|---|---:|---|
| `myclaw-core` | 42 | ReAct 循环（工具往返、死循环保护、流式、钩子、记忆）、技能注册与 SKILL.md 解析 |
| `myclaw-openai` | 30 | 协议编解码 + 用本地 `HttpServer` 打桩的端到端 HTTP/SSE/重试 |
| `myclaw-tools` | 86 | 各内置工具行为、沙箱越界、Shell 白名单、HTML 转纯文本 |
| `myclaw-cli` | 14 | Spring 容器集成：自动配置 + 注解扫描 + 技能扫描 + 完整工具调用链路 |

其中 `myclaw-openai` 的测试用 JDK 自带的 `com.sun.net.httpserver` 起本地桩服务，真实覆盖了请求构造、鉴权头、SSE 分片聚合、429/5xx 重试与错误信息解析。

## 扩展点

| 想做的事 | 做法 |
|---|---|
| 接入私有模型 | 实现 `ChatModel` |
| 自定义记忆存储（Redis / DB） | 实现 `Memory` |
| 埋点 / 链路追踪 / 审计 | 实现 `AgentHook`，或注册多个 |
| 动态生成的工具 Schema | 实现 `Tool`，`definition()` 里自由构造 |
| 替换默认 Agent 配置 | 定义自己的 `Agent` / `ToolRegistry` / `ChatModel` Bean |

## 已知环境问题

### Windows：`cmd.exe` 退出码异常导致 surefire 报 "Error occurred in starting fork"

**症状**：`mvn test` 输出里 `Tests run: N, Failures: 0` 全部通过，但最终以 `BUILD FAILURE` + `Error occurred in starting fork, check output in log` 收尾。

**根因**：本机注册表项

```
HKLM\Software\Microsoft\Command Processor\Autorun = "chcp 937"
```

`937` 不是合法代码页，于是**每次** `cmd.exe` 启动都会执行失败并把退出码留成 `1`。而 Maven Surefire 正是通过 `cmd.exe /X /C` 拉起测试 JVM 的，父进程拿到的退出码非 0，就判定 fork 启动失败。

可以这样确认：

```powershell
cmd.exe /X /C "echo hi"; $LASTEXITCODE        # 输出 1
cmd.exe /X /D /C "echo hi"; $LASTEXITCODE     # 输出 0（/D 禁用 AutoRun）
```

**解决办法**（任选其一）：

1. **推荐**：修正该注册表项（需要管理员权限）——把 `chcp 937` 删除，或改成合法代码页如 `chcp 936`。
2. **临时绕过**：让 Surefire 不 fork，测试在 Maven 进程内执行：

   ```bash
   mvn clean test -DforkCount=0
   ```

   注意这个参数只是绕过 fork 的退出码检查，测试本身的结果不受影响；但它会让测试与 Maven 共享 JVM，不建议写进 CI 配置。

## License

MIT
