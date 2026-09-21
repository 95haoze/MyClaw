package io.myclaw.core.agent;

import io.myclaw.core.exception.MaxIterationsException;
import io.myclaw.core.exception.ToolExecutionException;
import io.myclaw.core.exception.EmptyModelResponseException;
import io.myclaw.core.memory.InMemoryMemory;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.Role;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.model.StreamListener;
import io.myclaw.core.support.ScriptedChatModel;
import io.myclaw.core.support.TestTools;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import io.myclaw.core.tool.ToolRegistry;
import io.myclaw.core.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agent 循环的端到端测试 —— 全部离线、确定性执行，不依赖任何真实模型。
 */
class AgentTest {

    @Test
    @DisplayName("不需要工具时，一轮就返回答案")
    void answersWithoutTools() {
        ScriptedChatModel model = ScriptedChatModel.create().thenReply("你好，我是 MyClaw。");
        Agent agent = Agent.builder("chat").model(model).build();

        AgentResponse response = agent.run("你是谁？");

        assertThat(response.content()).isEqualTo("你好，我是 MyClaw。");
        assertThat(response.iterations()).isEqualTo(1);
        assertThat(response.usedTools()).isFalse();
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("完整的工具调用链路：模型请求 -> 执行 -> 结果回填 -> 最终答案")
    void completesToolCallRoundTrip() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("add", "{\"a\":2,\"b\":3}")
                .thenReply("2 + 3 = 5");
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());
        Agent agent = Agent.builder("calc").model(model).tools(registry).build();

        AgentResponse response = agent.run("帮我算一下 2+3");

        assertThat(response.content()).isEqualTo("2 + 3 = 5");
        assertThat(response.iterations()).isEqualTo(2);
        assertThat(response.usedTools()).isTrue();
        assertThat(response.toolResults()).singleElement().satisfies(result -> {
            assertThat(result.error()).isFalse();
            assertThat(result.toolName()).isEqualTo("add");
            assertThat(result.content()).isEqualTo("5");
        });

        // 第二次请求必须把工具执行结果带回去，否则模型无从续答
        List<Message> secondRound = model.requests().get(1).messages();
        assertThat(secondRound).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo(Role.TOOL);
            assertThat(message.content()).isEqualTo("5");
        });
        // 且 assistant 的 tool_calls 消息必须存在，tool_call_id 才能对得上
        assertThat(secondRound).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo(Role.ASSISTANT);
            assertThat(message.toolCalls()).hasSize(1);
        });
    }

    @Test
    @DisplayName("注册的工具会被暴露给模型")
    void advertisesRegisteredTools() {
        ScriptedChatModel model = ScriptedChatModel.create().thenReply("ok");
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());
        Agent agent = Agent.builder("t").model(model).tools(registry).build();

        agent.run("hi");

        assertThat(model.lastRequest().tools())
                .extracting(ToolDefinition::name)
                .containsExactly("add");
    }

    @Test
    @DisplayName("没有注册任何工具时，不向模型暴露 tools 字段")
    void omitsToolsWhenRegistryIsEmpty() {
        ScriptedChatModel model = ScriptedChatModel.create().thenReply("ok");
        Agent agent = Agent.builder("t").model(model).build();

        agent.run("hi");

        assertThat(model.lastRequest().hasTools()).isFalse();
    }

    @Test
    @DisplayName("调用不存在的工具时，错误结果回填给模型而不是抛异常")
    void feedsUnknownToolErrorBackToModel() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("no_such_tool", "{}")
                .thenReply("抱歉，我换一种方式。");
        Agent agent = Agent.builder("t").model(model).build();

        AgentResponse response = agent.run("调用一个不存在的工具");

        assertThat(response.content()).isEqualTo("抱歉，我换一种方式。");
        assertThat(response.toolResults()).singleElement().satisfies(result -> {
            assertThat(result.error()).isTrue();
            assertThat(result.content()).contains("不存在");
        });
    }

    @Test
    @DisplayName("工具内部抛异常时被捕获，Agent 循环继续")
    void survivesToolException() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("explode", "{}")
                .thenReply("工具挂了，我直接回答。");
        ToolRegistry registry = new ToolRegistry().register(TestTools.exploding());
        Agent agent = Agent.builder("t").model(model).tools(registry).build();

        AgentResponse response = agent.run("go");

        assertThat(response.content()).isEqualTo("工具挂了，我直接回答。");
        assertThat(response.toolResults()).singleElement().satisfies(result -> {
            assertThat(result.error()).isTrue();
            assertThat(result.content()).contains("数据库连接失败");
        });
    }

    @Test
    @DisplayName("模型陷入工具调用死循环时，达到上限抛 MaxIterationsException")
    void guardsAgainstInfiniteToolLoop() {
        ScriptedChatModel model = ScriptedChatModel.create();
        for (int i = 0; i < 10; i++) {
            model.thenCallTool("add", "{\"a\":1,\"b\":1}");
        }
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());
        Agent agent = Agent.builder("loop").model(model).tools(registry).maxIterations(3).build();

        assertThatThrownBy(() -> agent.run("陷入循环"))
                .isInstanceOf(MaxIterationsException.class)
                .hasMessageContaining("3");

        assertThat(model.callCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("记忆跨多次 run 复用，形成多轮对话")
    void keepsConversationHistory() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenReply("第一次回答")
                .thenReply("第二次回答");
        Agent agent = Agent.builder("m").model(model).systemPrompt(null).build();

        agent.run("你好");
        agent.run("再见");

        assertThat(model.lastRequest().messages()).hasSize(3);
        assertThat(model.lastRequest().messages())
                .extracting(Message::role)
                .containsExactly(Role.USER, Role.ASSISTANT, Role.USER);
    }

    @Test
    @DisplayName("系统提示词被注入到消息列表最前面")
    void injectsSystemPrompt() {
        ScriptedChatModel model = ScriptedChatModel.create().thenReply("ok");
        Agent agent = Agent.builder("t").model(model).systemPrompt("你是测试助手").build();

        agent.run("hi");

        Message first = model.lastRequest().messages().getFirst();
        assertThat(first.role()).isEqualTo(Role.SYSTEM);
        assertThat(first.content()).isEqualTo("你是测试助手");
    }

    @Test
    @DisplayName("单次请求可以覆盖 Agent 的系统提示词")
    void requestCanOverrideSystemPrompt() {
        ScriptedChatModel model = ScriptedChatModel.create().thenReply("ok");
        Agent agent = Agent.builder("t").model(model).systemPrompt("默认人格").build();

        agent.run(AgentRequest.builder().input("hi").systemPrompt("临时人格").build());

        assertThat(model.lastRequest().messages().getFirst().content()).isEqualTo("临时人格");
    }

    @Test
    @DisplayName("流式输出：文本增量被逐段回调")
    void streamsTextDeltas() {
        ScriptedChatModel model = ScriptedChatModel.create().chunkedStreaming().thenReply("你好世界");
        Agent agent = Agent.builder("s").model(model).systemPrompt(null).build();

        List<String> deltas = new ArrayList<>();
        AgentResponse response = agent.run("hi", StreamListener.onText(deltas::add));

        assertThat(response.content()).isEqualTo("你好世界");
        assertThat(deltas).hasSize(4);
        assertThat(String.join("", deltas)).isEqualTo("你好世界");
    }

    @Test
    @DisplayName("钩子按预期顺序触发")
    void firesHooksInOrder() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("add", "{\"a\":1,\"b\":2}")
                .thenReply("3");
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());
        List<String> events = new ArrayList<>();

        Agent agent = Agent.builder("hooked")
                .model(model)
                .tools(registry)
                .hook(new AgentHook() {
                    @Override
                    public void onAgentStart(Agent a, AgentRequest r) {
                        events.add("start");
                    }

                    @Override
                    public void onModelRequest(Agent a, ChatRequest r) {
                        events.add("modelRequest");
                    }

                    @Override
                    public void onModelResponse(Agent a, ChatResponse r) {
                        events.add("modelResponse");
                    }

                    @Override
                    public void onToolCall(Agent a, ToolCall c, ToolResult r) {
                        events.add("tool:" + c.name());
                    }

                    @Override
                    public void onAgentEnd(Agent a, AgentResponse r) {
                        events.add("end");
                    }
                })
                .build();

        agent.run("go");

        assertThat(events).containsExactly(
                "start", "modelRequest", "modelResponse", "tool:add", "modelRequest", "modelResponse", "end");
    }

    @Test
    @DisplayName("钩子自身抛异常不会影响 Agent 主流程")
    void brokenHookDoesNotBreakAgent() {
        ScriptedChatModel model = ScriptedChatModel.create().thenReply("正常回答");
        Agent agent = Agent.builder("t")
                .model(model)
                .hook(new AgentHook() {
                    @Override
                    public void onAgentStart(Agent a, AgentRequest r) {
                        throw new IllegalStateException("观测组件炸了");
                    }
                })
                .build();

        assertThat(agent.run("hi").content()).isEqualTo("正常回答");
    }

    @Test
    @DisplayName("工具上下文正确携带 Agent 名称、工作目录与自定义属性")
    void passesToolContext() {
        TestTools.Recorder recorder = new TestTools.Recorder("rec");
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("rec", "{\"payload\":\"hello\"}")
                .thenReply("done");
        Path workDir = Path.of("target", "test-work");

        Agent agent = Agent.builder("ctx-agent")
                .model(model)
                .tools(new ToolRegistry().register(recorder))
                .toolContext(ToolContext.of(workDir))
                .build();

        agent.run(AgentRequest.builder().input("go").attribute("sessionId", "s-1").build());

        ToolContext context = recorder.contexts().getFirst();
        assertThat(context.agentName()).isEqualTo("ctx-agent");
        assertThat(context.workingDirectory()).isEqualTo(workDir.toAbsolutePath().normalize());
        assertThat(context.attribute("sessionId")).contains("s-1");
        assertThat(recorder.invocations().getFirst().path("payload").asString()).isEqualTo("hello");
    }

    @Test
    @DisplayName("工具失败后模型返回空内容时保留工具失败语义")
    void reportsToolFailureWhenModelReturnsEmptyAfterFailure() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenCallTool("explode", "{}")
                .thenReply("   ");
        Agent agent = Agent.builder("t")
                .model(model)
                .tools(new ToolRegistry().register(TestTools.exploding()))
                .build();

        assertThatThrownBy(() -> agent.run("go"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("explode");
    }
    @Test
    @DisplayName("模型返回空内容时抛出明确异常")
    void rejectsEmptyModelResponse() {
        Agent agent = Agent.builder("t").model(ScriptedChatModel.create().thenReply("   ")).build();

        assertThatThrownBy(() -> agent.run("hi"))
                .isInstanceOf(EmptyModelResponseException.class)
                .hasMessageContaining("模型未返回任何有效内容");
        assertThat(agent.memory().messages())
                .extracting(Message::role)
                .containsExactly(Role.USER);
    }
    @Test
    @DisplayName("空输入直接抛 IllegalArgumentException")
    void rejectsBlankInput() {
        Agent agent = Agent.builder("t").model(ScriptedChatModel.create().thenReply("x")).build();

        assertThatThrownBy(() -> agent.run("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("输入不能为空");
    }

    @Test
    @DisplayName("withMemory 派生的实例共享配置但隔离记忆")
    void withMemoryIsolatesHistory() {
        ScriptedChatModel model = ScriptedChatModel.create()
                .thenReply("a").thenReply("b").thenReply("c");
        Agent base = Agent.builder("t").model(model).systemPrompt(null).build();
        Agent session1 = base.withMemory(new InMemoryMemory());
        Agent session2 = base.withMemory(new InMemoryMemory());

        session1.run("问题一");
        session2.run("问题二");
        session1.run("问题一 follow-up");

        // session2 的第二次请求不应该看到 session1 的历史
        assertThat(session2.memory().size()).isEqualTo(2);
        assertThat(session1.memory().size()).isEqualTo(4);
    }

    @Test
    @DisplayName("模型不支持 function calling 时不暴露工具")
    void hidesToolsWhenModelDoesNotSupportThem() {
        ScriptedChatModel scripted = ScriptedChatModel.create().thenReply("ok");
        io.myclaw.core.model.ChatModel noTools = new io.myclaw.core.model.ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return scripted.chat(request);
            }

            @Override
            public boolean supportsToolCalling() {
                return false;
            }
        };
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());
        Agent agent = Agent.builder("t").model(noTools).tools(registry).build();

        agent.run("hi");

        assertThat(scripted.lastRequest().hasTools()).isFalse();
    }
}
