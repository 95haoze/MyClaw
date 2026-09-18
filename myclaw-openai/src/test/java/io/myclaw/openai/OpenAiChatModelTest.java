package io.myclaw.openai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.myclaw.core.exception.ModelException;
import io.myclaw.core.json.Json;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.model.StreamListener;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.ToolDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OpenAiChatModel} 的端到端测试。
 *
 * <p>用 JDK 自带的 {@link HttpServer} 起一个本地桩服务，因此测试是<b>完全离线且确定性</b>的，
 * 但依然真实覆盖了 HTTP 请求构造、鉴权头、JSON 编解码、SSE 解析与重试逻辑。
 */
class OpenAiChatModelTest {

    /** 一次桩响应。 */
    private record Stub(int status, String contentType, String body) {
    }

    /** 捕获到的请求，用于断言客户端发出去的东西。 */
    private record Captured(String authorization, String body) {
    }

    private HttpServer server;
    private final Deque<Stub> stubs = new ArrayDeque<>();
    private final List<Captured> captured = new ArrayList<>();
    private OpenAiChatModel model;

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();

        OpenAiConfig config = OpenAiConfig.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .apiKey("sk-test-key")
                .model("qwen-plus")
                .maxRetries(0)
                .build();
        model = OpenAiChatModel.of(config);
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        captured.add(new Captured(exchange.getRequestHeaders().getFirst("Authorization"), body));

        Stub stub = stubs.poll();
        if (stub == null) {
            stub = json(500, "{\"error\":{\"message\":\"测试桩没有排入响应\"}}");
        }
        byte[] payload = stub.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", stub.contentType());
        exchange.sendResponseHeaders(stub.status(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    // ------------------------------------------------------------------ 桩构造工具

    private static Stub json(int status, String body) {
        return new Stub(status, "application/json", body);
    }

    private static Stub sse(String body) {
        return new Stub(200, "text/event-stream", body);
    }

    /** 构造一个 text 类型的普通响应体。 */
    private static String textResponse(String content, String finishReason) {
        ObjectNode root = Json.obj();
        root.put("model", "qwen-plus");
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finishReason);
        choice.putObject("message").put("role", "assistant").put("content", content);
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", 9);
        usage.put("completion_tokens", 4);
        usage.put("total_tokens", 13);
        return Json.write(root);
    }

    /** 构造一个工具调用响应体。 */
    private static String toolCallResponse(String id, String name, String arguments) {
        ObjectNode root = Json.obj();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "tool_calls");
        ObjectNode message = choice.putObject("message");
        message.putNull("content");
        ObjectNode call = message.putArray("tool_calls").addObject();
        call.put("id", id);
        call.put("type", "function");
        call.putObject("function").put("name", name).put("arguments", arguments);
        return Json.write(root);
    }

    /** 构造一个 SSE delta 分片。 */
    private static String delta(Consumer<ObjectNode> customizer) {
        ObjectNode chunk = Json.obj();
        ObjectNode choice = chunk.putArray("choices").addObject();
        choice.put("index", 0);
        ObjectNode delta = choice.putObject("delta");
        customizer.accept(delta);
        return "data: " + Json.write(chunk) + "\n\n";
    }

    private static String deltaText(String text) {
        return delta(d -> d.put("content", text));
    }

    private static String deltaToolCall(String id, String name, String argumentsFragment) {
        return delta(d -> {
            ObjectNode call = d.putArray("tool_calls").addObject();
            call.put("index", 0);
            if (id != null) {
                call.put("id", id);
                call.put("type", "function");
            }
            call.putObject("function").put("name", name).put("arguments", argumentsFragment);
        });
    }

    /** 构造一个只带 finish_reason 的结束分片。 */
    private static String finish(String reason) {
        ObjectNode chunk = Json.obj();
        ObjectNode choice = chunk.putArray("choices").addObject();
        choice.put("index", 0);
        choice.putObject("delta");
        choice.put("finish_reason", reason);
        return "data: " + Json.write(chunk) + "\n\n";
    }

    private static String sseDone() {
        return "data: [DONE]\n\n";
    }

    private static ChatRequest simpleRequest() {
        return ChatRequest.builder().messages(List.of(Message.user("你好"))).build();
    }

    // ------------------------------------------------------------------ 非流式

    @Test
    @DisplayName("发送 Bearer 鉴权头并解析文本响应")
    void sendsAuthorizationAndParsesText() {
        stubs.add(json(200, textResponse("你好，我是 MyClaw", "stop")));

        ChatResponse response = model.chat(simpleRequest());

        assertThat(response.content()).isEqualTo("你好，我是 MyClaw");
        assertThat(response.usage().totalTokens()).isEqualTo(13);
        assertThat(captured).singleElement()
                .satisfies(request -> assertThat(request.authorization()).isEqualTo("Bearer sk-test-key"));
    }

    @Test
    @DisplayName("请求体包含模型名、消息与工具定义")
    void sendsExpectedRequestBody() {
        stubs.add(json(200, textResponse("ok", "stop")));
        ToolDefinition definition = ToolDefinition.builder("get_weather")
                .description("查天气")
                .parameters(JsonSchema.object().string("city", "城市").build())
                .build();

        model.chat(ChatRequest.builder()
                .messages(List.of(Message.system("sys"), Message.user("杭州天气")))
                .tools(List.of(definition))
                .build());

        JsonNode body = Json.parse(captured.getFirst().body());
        assertThat(body.path("model").asString()).isEqualTo("qwen-plus");
        assertThat(body.path("messages")).hasSize(2);
        assertThat(body.path("messages").get(0).path("content").asString()).isEqualTo("sys");
        assertThat(body.path("tools").get(0).path("function").path("name").asString()).isEqualTo("get_weather");
        assertThat(body.path("stream").asBoolean(false)).isFalse();
    }

    @Test
    @DisplayName("解析工具调用响应")
    void parsesToolCallResponse() {
        stubs.add(json(200, toolCallResponse("call_1", "get_weather", "{\"city\":\"杭州\"}")));

        ChatResponse response = model.chat(simpleRequest());

        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call_1");
            assertThat(call.name()).isEqualTo("get_weather");
        });
    }

    // ------------------------------------------------------------------ 错误与重试

    @Test
    @DisplayName("非 2xx 抛出 ModelException，并带上厂商的错误信息")
    void throwsModelExceptionWithProviderMessage() {
        stubs.add(json(401, "{\"error\":{\"message\":\"Invalid API key\",\"type\":\"invalid_request_error\"}}"));

        assertThatThrownBy(() -> model.chat(simpleRequest()))
                .isInstanceOf(ModelException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Invalid API key");
    }

    @Test
    @DisplayName("5xx 会重试，重试成功后正常返回")
    void retriesOnServerError() {
        OpenAiChatModel retrying = OpenAiChatModel.of(model.config().toBuilder().maxRetries(2).build());
        stubs.add(json(503, "{\"error\":{\"message\":\"service unavailable\"}}"));
        stubs.add(json(200, textResponse("重试成功", "stop")));

        ChatResponse response = retrying.chat(simpleRequest());

        assertThat(response.content()).isEqualTo("重试成功");
        assertThat(captured).hasSize(2);
    }

    @Test
    @DisplayName("401 这类不可重试的错误只请求一次")
    void doesNotRetryOnClientError() {
        OpenAiChatModel retrying = OpenAiChatModel.of(model.config().toBuilder().maxRetries(3).build());
        stubs.add(json(401, "{\"error\":{\"message\":\"unauthorized\"}}"));

        assertThatThrownBy(() -> retrying.chat(simpleRequest())).isInstanceOf(ModelException.class);
        assertThat(captured).hasSize(1);
    }

    // ------------------------------------------------------------------ 流式

    @Test
    @DisplayName("流式输出：文本增量逐段回调，最终聚合出完整答案")
    void streamsTextDeltas() {
        stubs.add(sse(deltaText("杭") + deltaText("州") + deltaText("晴") + finish("stop") + sseDone()));

        List<String> deltas = new ArrayList<>();
        ChatResponse response = model.stream(simpleRequest(), StreamListener.onText(deltas::add));

        assertThat(deltas).containsExactly("杭", "州", "晴");
        assertThat(response.content()).isEqualTo("杭州晴");
        assertThat(response.finishReason()).isEqualTo("stop");
    }

    @Test
    @DisplayName("流式工具调用：分片参数被按 index 正确聚合")
    void aggregatesStreamedToolCallFragments() {
        stubs.add(sse(
                deltaToolCall("call_1", "get_weather", "")
                        + deltaToolCall(null, "", "{\"ci")
                        + deltaToolCall(null, "", "ty\":\"杭州\"}")
                        + finish("tool_calls")
                        + sseDone()));

        ChatResponse response = model.stream(simpleRequest(), new StreamListener() {
        });

        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call_1");
            assertThat(call.name()).isEqualTo("get_weather");
            assertThat(call.arguments()).isEqualTo("{\"city\":\"杭州\"}");
        });
    }

    @Test
    @DisplayName("流式请求会带上 stream=true 且 Accept 为 text/event-stream")
    void sendsStreamingRequest() {
        stubs.add(sse(deltaText("ok") + finish("stop") + sseDone()));

        model.stream(simpleRequest(), new StreamListener() {
        });

        JsonNode body = Json.parse(captured.getFirst().body());
        assertThat(body.path("stream").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("流式过程中读取到非法 JSON 时抛出 ModelException")
    void failsOnMalformedStreamChunk() {
        stubs.add(sse("data: {这不是合法JSON}\n\n" + sseDone()));

        assertThatThrownBy(() -> model.stream(simpleRequest(), new StreamListener() {
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法解析 JSON");
    }

    @Test
    @DisplayName("流式调用中途出错会回调 onError")
    void notifiesListenerOnStreamError() {
        stubs.add(json(500, "{\"error\":{\"message\":\"boom\"}}"));
        List<Throwable> errors = new ArrayList<>();

        assertThatThrownBy(() -> model.stream(simpleRequest(), new StreamListener() {
            @Override
            public void onError(Throwable error) {
                errors.add(error);
            }
        })).isInstanceOf(ModelException.class);

        assertThat(errors).singleElement().isInstanceOf(ModelException.class);
    }

    @Test
    @DisplayName("工具调用与文本可以在同一次流式响应中共存")
    void handlesMixedStream() {
        stubs.add(sse(
                deltaText("我来查一下。")
                        + deltaToolCall("call_9", "get_weather", "{}")
                        + finish("tool_calls")
                        + sseDone()));

        List<String> deltas = new ArrayList<>();
        ChatResponse response = model.stream(simpleRequest(), StreamListener.onText(deltas::add));

        assertThat(String.join("", deltas)).isEqualTo("我来查一下。");
        assertThat(response.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.name()).isEqualTo("get_weather"));
    }

    @Test
    @DisplayName("模型名与配置一致")
    void exposesModelName() {
        assertThat(model.name()).isEqualTo("openai:qwen-plus");
    }

    @Test
    @DisplayName("工具结果消息回填时能被正确编码成 tool 角色")
    void encodesToolResultMessageOnSecondRound() {
        stubs.add(json(200, textResponse("答案是 5", "stop")));

        model.chat(ChatRequest.builder()
                .messages(List.of(
                        Message.user("1+4"),
                        Message.assistant(null, List.of(new ToolCall("call_x", "add", "{\"a\":1,\"b\":4}"))),
                        Message.tool("call_x", "add", "5")))
                .build());

        JsonNode messages = Json.parse(captured.getFirst().body()).path("messages");
        assertThat(messages.get(1).path("tool_calls").get(0).path("id").asString()).isEqualTo("call_x");
        // 带 tool_calls 的 assistant 消息 content 用空串，兼容性最好
        assertThat(messages.get(1).path("content").asString()).isEmpty();
        assertThat(messages.get(2).path("role").asString()).isEqualTo("tool");
        assertThat(messages.get(2).path("tool_call_id").asString()).isEqualTo("call_x");
    }
}
