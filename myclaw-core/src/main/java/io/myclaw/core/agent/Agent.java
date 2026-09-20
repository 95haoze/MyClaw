package io.myclaw.core.agent;

import io.myclaw.core.exception.MaxIterationsException;
import io.myclaw.core.exception.ToolExecutionException;
import io.myclaw.core.memory.InMemoryMemory;
import io.myclaw.core.memory.Memory;
import io.myclaw.core.message.ChatUsage;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatModel;
import io.myclaw.core.model.ChatOptions;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.model.StreamListener;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import io.myclaw.core.tool.ToolRegistry;
import io.myclaw.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * MyClaw 的核心 —— 一个 ReAct（Reasoning + Acting）风格的 Agent 循环。
 *
 * <p>一次 {@link #run(AgentRequest)} 的执行流程：
 * <pre>
 *   用户输入 ──► 写入记忆
 *        │
 *        ▼
 *   ┌──────────────────────────────────────────────┐
 *   │ 1. 组装请求 = [system] + 记忆 + 工具定义        │
 *   │ 2. 调用模型                                   │
 *   │ 3. 模型要调用工具？                            │
 *   │      ├─ 是 ─► 执行工具 ─► 结果写回记忆 ─► 回到 1 │
 *   │      └─ 否 ─► 产出最终答案，结束                 │
 *   └──────────────────────────────────────────────┘
 * </pre>
 *
 * <p>{@code Agent} 实例是<b>无状态</b>的（状态全在 {@link Memory} 里），因此可以安全地被
 * 多个线程共享 —— 只要每个会话使用独立的 {@link Memory}。
 * 需要独立会话时，用 {@link #withMemory(Memory)} 派生一个新实例。
 *
 * <p>创建实例请使用 {@link #builder(String)}：
 * <pre>{@code
 * Agent agent = Agent.builder("assistant")
 *         .model(new OpenAiChatModel(config))
 *         .systemPrompt(AgentPrompts.DEFAULT)
 *         .tools(registry)
 *         .maxIterations(8)
 *         .hook(new LoggingHook())
 *         .build();
 *
 * AgentResponse response = agent.run("杭州现在天气怎么样？");
 * System.out.println(response.content());
 * }</pre>
 */
public final class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    /** 默认最大迭代轮数，防止模型陷入工具调用死循环。 */
    public static final int DEFAULT_MAX_ITERATIONS = 10;

    private final String name;
    private final String systemPrompt;
    private final ChatModel model;
    private final ToolRegistry tools;
    private final Memory memory;
    private final ChatOptions options;
    private final int maxIterations;
    private final List<AgentHook> hooks;
    private final ToolContext toolContext;

    private Agent(Builder builder) {
        this.name = builder.name;
        this.systemPrompt = builder.systemPrompt;
        this.model = Objects.requireNonNull(builder.model, "必须为 Agent 指定 ChatModel");
        this.tools = builder.tools == null ? new ToolRegistry() : builder.tools;
        this.memory = builder.memory == null ? new InMemoryMemory() : builder.memory;
        this.options = builder.options == null ? ChatOptions.EMPTY : builder.options;
        this.maxIterations = builder.maxIterations;
        this.hooks = List.copyOf(builder.hooks);
        this.toolContext = builder.toolContext;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ------------------------------- 访问器 -----------------------------------

    public String name() {
        return name;
    }

    public ChatModel model() {
        return model;
    }

    public ToolRegistry tools() {
        return tools;
    }

    /** 当前记忆实例（可变，谨慎共享）。 */
    public Memory memory() {
        return memory;
    }

    public ChatOptions options() {
        return options;
    }

    public int maxIterations() {
        return maxIterations;
    }

    public List<AgentHook> hooks() {
        return hooks;
    }

    public ToolContext toolContext() {
        return toolContext;
    }

    // ------------------------------ 执行 ------------------------------------

    /** 用默认配置执行一次对话。 */
    public AgentResponse run(String input) {
        return run(AgentRequest.of(input));
    }

    /** 以流式方式执行一次对话，文本增量实时回调到 {@code listener}。 */
    public AgentResponse run(String input, StreamListener listener) {
        return run(AgentRequest.builder().input(input).streaming(listener).build());
    }

    /**
     * 执行一次完整的 Agent 循环。
     *
     * <p>用户输入与模型的每一次回复都会写入 {@link #memory()}，因此同一个 Agent 实例
     * 天然支持多轮对话。
     *
     * @throws MaxIterationsException 达到 {@link #maxIterations} 仍未产出最终答案
     */
    public AgentResponse run(AgentRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("输入不能为空");
        }

        long startedAt = System.nanoTime();
        List<AgentStep> steps = new ArrayList<>();
        ChatUsage totalUsage = ChatUsage.EMPTY;
        ToolContext context = buildToolContext(request);

        memory.add(Message.user(request.input()));
        fireHooks(hook -> hook.onAgentStart(this, request));

        try {
            int iteration = 0;
            int consecutiveFailedToolRounds = 0;
            while (true) {
                if (request.listener() != null && request.listener().isCancelled()) {
                    throw new CancellationException("Agent 执行已取消");
                }
                iteration++;
                if (iteration > maxIterations) {
                    throw new MaxIterationsException(maxIterations);
                }

                boolean forceFinalAnswer = iteration == maxIterations && !steps.isEmpty();
                ChatRequest chatRequest = buildChatRequest(request, forceFinalAnswer);
                if (request.listener() != null) request.listener().onModelRequest(iteration);
                fireHooks(hook -> hook.onModelRequest(this, chatRequest));

                ChatResponse response = invoke(chatRequest, request);
                fireHooks(hook -> hook.onModelResponse(this, response));

                totalUsage = totalUsage.plus(response.usage());
                Message assistant = response.message();
                memory.add(assistant);

                List<ToolCall> toolCalls = assistant.toolCalls();
                if (forceFinalAnswer && !toolCalls.isEmpty()) {
                    throw new MaxIterationsException(maxIterations);
                }
                if (toolCalls.isEmpty()) {
                    String answer = assistant.content() == null ? "" : assistant.content();
                    steps.add(new AgentStep.Answer(iteration, answer, response.usage()));
                    AgentResponse result = new AgentResponse(
                            answer, steps, totalUsage, iteration, elapsedMillis(startedAt));
                    fireHooks(hook -> hook.onAgentEnd(this, result));
                    return result;
                }

                // 工具阶段：执行 -> 结果写回记忆 -> 进入下一轮
                log.debug("[{}] 第 {} 轮请求调用 {} 个工具: {}",
                        name, iteration, toolCalls.size(), toolCalls.stream().map(ToolCall::name).toList());
                if (request.listener() != null && request.listener().isCancelled()) {
                    throw new CancellationException("Agent 执行已取消");
                }
                if (request.listener() != null) request.listener().onToolsStart(iteration, toolCalls);
                List<ToolResult> results = tools.executeAll(toolCalls, context);
                if (!results.isEmpty() && results.stream().allMatch(ToolResult::error)) {
                    consecutiveFailedToolRounds++;
                    if (consecutiveFailedToolRounds >= 2) {
                        String failedTools = results.stream().map(ToolResult::toolName).distinct().toList().toString();
                        throw new ToolExecutionException("Tools failed repeatedly: " + failedTools);
                    }
                } else {
                    consecutiveFailedToolRounds = 0;
                }
                for (ToolResult toolResult : results) {
                    memory.add(Message.tool(toolResult.toolCallId(), toolResult.toolName(), toolResult.toModelContent()));
                }
                for (int i = 0; i < toolCalls.size(); i++) {
                    ToolCall call = toolCalls.get(i);
                    ToolResult result = results.get(i);
                    fireHooks(hook -> hook.onToolCall(this, call, result));
                    if (request.listener() != null) request.listener().onToolComplete(iteration, call, result);
                }
                steps.add(new AgentStep.ToolUse(iteration, toolCalls, results));
            }
        } catch (RuntimeException e) {
            fireHooks(hook -> hook.onError(this, e));
            throw e;
        }
    }

    // ------------------------------- 内部 -----------------------------------

    private ChatResponse invoke(ChatRequest chatRequest, AgentRequest request) {
        StreamListener listener = request.listener();
        return listener == null
                ? model.chat(chatRequest)
                : model.stream(chatRequest, listener);
    }

    private ChatRequest buildChatRequest(AgentRequest request, boolean forceFinalAnswer) {
        List<Message> messages = new ArrayList<>();
        String effectiveSystemPrompt =
                request.systemPrompt() != null ? request.systemPrompt() : this.systemPrompt;
        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isBlank()) {
            messages.add(Message.system(effectiveSystemPrompt));
        }
        messages.addAll(memory.messages());
        if (forceFinalAnswer) {
            messages.add(Message.system("工具调用轮数即将达到上限。不要再调用任何工具，请根据已有信息直接给出最终结果，并明确说明尚未完成的部分。"));
        }

        List<ToolDefinition> definitions = forceFinalAnswer || tools.isEmpty() || !model.supportsToolCalling()
                ? List.of()
                : tools.definitions();

        return ChatRequest.builder()
                .messages(messages)
                .tools(definitions)
                .options(options.merge(request.options()))
                .build();
    }

    private ToolContext buildToolContext(AgentRequest request) {
        ToolContext.Builder builder = toolContext.toBuilder().agentName(name);
        if (!request.attributes().isEmpty()) {
            builder.attributes(request.attributes());
        }
        return builder.build();
    }

    private void fireHooks(java.util.function.Consumer<AgentHook> action) {
        for (AgentHook hook : hooks) {
            try {
                action.accept(hook);
            } catch (RuntimeException e) {
                // 观测组件不应该影响主流程
                log.warn("AgentHook {} 执行异常，已忽略: {}", hook.getClass().getSimpleName(), e.toString());
            }
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    // ------------------------------------------------------------------ 派生

    /** 清空记忆，开始一段全新的对话。 */
    public void reset() {
        memory.clear();
    }

    /** 派生一个共享全部配置、但使用独立记忆的新 Agent，用于隔离并发会话。 */
    public Agent withMemory(Memory newMemory) {
        return toBuilder().memory(newMemory).build();
    }

    /** 导出当前配置，便于派生变体。 */
    public Builder toBuilder() {
        return new Builder(name)
                .systemPrompt(systemPrompt)
                .model(model)
                .tools(tools)
                .memory(memory)
                .options(options)
                .maxIterations(maxIterations)
                .hooks(hooks)
                .toolContext(toolContext);
    }

    @Override
    public String toString() {
        return "Agent(name=" + name + ", model=" + model.name()
                + ", tools=" + tools.names() + ", maxIterations=" + maxIterations + ")";
    }

    // ------------------------------------------------------------------ Builder

    public static final class Builder {
        private final String name;
        private String systemPrompt = AgentPrompts.DEFAULT;
        private ChatModel model;
        private ToolRegistry tools;
        private Memory memory;
        private ChatOptions options = ChatOptions.EMPTY;
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private final List<AgentHook> hooks = new ArrayList<>();
        private ToolContext toolContext = ToolContext.defaults();

        private Builder(String name) {
            this.name = name == null || name.isBlank() ? "agent" : name;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder model(ChatModel model) {
            this.model = model;
            return this;
        }

        public Builder tools(ToolRegistry tools) {
            this.tools = tools;
            return this;
        }

        public Builder memory(Memory memory) {
            this.memory = memory;
            return this;
        }

        public Builder options(ChatOptions options) {
            this.options = options;
            return this;
        }

        public Builder temperature(double temperature) {
            this.options = this.options.merge(ChatOptions.builder().temperature(temperature).build());
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            if (maxIterations < 1) {
                throw new IllegalArgumentException("maxIterations 至少为 1");
            }
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder hook(AgentHook hook) {
            this.hooks.add(hook);
            return this;
        }

        public Builder hooks(List<AgentHook> hooks) {
            this.hooks.addAll(hooks);
            return this;
        }

        public Builder toolContext(ToolContext toolContext) {
            this.toolContext = toolContext;
            return this;
        }

        public Builder workingDirectory(Path workingDirectory) {
            this.toolContext = this.toolContext.toBuilder().workingDirectory(workingDirectory).build();
            return this;
        }

        public Agent build() {
            return new Agent(this);
        }
    }
}
