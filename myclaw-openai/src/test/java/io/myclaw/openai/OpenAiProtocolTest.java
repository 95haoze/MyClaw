package io.myclaw.openai;

import io.myclaw.core.exception.ModelException;
import io.myclaw.core.json.Json;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatOptions;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 协议编解码层的单元测试：只验证"翻译"是否正确，不涉及网络。
 */
class OpenAiProtocolTest {

    private static final OpenAiConfig CONFIG = OpenAiConfig.builder()
            .baseUrl("https://example.com/v1")
            .apiKey("sk-test")
            .model("qwen-plus")
            .temperature(0.3)
            .maxTokens(512)
            .build();

    // ------------------------------------------------------------------ 请求编码

    @Test
    @DisplayName("四种角色的消息都被正确编码")
    void encodesAllMessageRoles() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        Message.system("你是助手"),
                        Message.user("杭州天气？"),
                        Message.assistant("我来查一下", List.of(new ToolCall("call_1", "get_weather", "{\"city\":\"杭州\"}"))),
                        Message.tool("call_1", "get_weather", "晴 26℃")))
                .build();

        JsonNode messages = OpenAiProtocol.buildBody(CONFIG, request, false).path("messages");

        assertThat(messages).hasSize(4);

        assertThat(messages.get(0).path("role").asString()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asString()).isEqualTo("你是助手");

        assertThat(messages.get(1).path("role").asString()).isEqualTo("user");

        JsonNode assistant = messages.get(2);
        assertThat(assistant.path("role").asString()).isEqualTo("assistant");
        assertThat(assistant.path("content").asString()).isEqualTo("我来查一下");
        JsonNode toolCall = assistant.path("tool_calls").get(0);
        assertThat(toolCall.path("id").asString()).isEqualTo("call_1");
        assertThat(toolCall.path("type").asString()).isEqualTo("function");
        assertThat(toolCall.path("function").path("name").asString()).isEqualTo("get_weather");
        assertThat(toolCall.path("function").path("arguments").asString()).isEqualTo("{\"city\":\"杭州\"}");

        JsonNode toolMessage = messages.get(3);
        assertThat(toolMessage.path("role").asString()).isEqualTo("tool");
        assertThat(toolMessage.path("tool_call_id").asString()).isEqualTo("call_1");
        assertThat(toolMessage.path("content").asString()).isEqualTo("晴 26℃");
    }

    @Test
    @DisplayName("没有工具时不下发 tools 与 tool_choice 字段")
    void omitsToolsWhenAbsent() {
        JsonNode body = OpenAiProtocol.buildBody(CONFIG, ChatRequest.builder()
                .messages(List.of(Message.user("hi")))
                .build(), false);

        assertThat(body.has("tools")).isFalse();
        assertThat(body.has("tool_choice")).isFalse();
    }

    @Test
    @DisplayName("工具定义被编码为 OpenAI function 结构，且默认 tool_choice=auto")
    void encodesToolDefinitions() {
        ToolDefinition definition = ToolDefinition.builder("get_weather")
                .description("查询城市天气")
                .parameters(JsonSchema.object().string("city", "城市名").build())
                .build();

        JsonNode body = OpenAiProtocol.buildBody(CONFIG, ChatRequest.builder()
                .messages(List.of(Message.user("hi")))
                .tools(List.of(definition))
                .build(), false);

        JsonNode tool = body.path("tools").get(0);
        assertThat(tool.path("type").asString()).isEqualTo("function");
        assertThat(tool.path("function").path("name").asString()).isEqualTo("get_weather");
        assertThat(tool.path("function").path("description").asString()).isEqualTo("查询城市天气");
        assertThat(tool.path("function").path("parameters").path("properties").path("city").path("type").asString())
                .isEqualTo("string");
        assertThat(body.path("tool_choice").asString()).isEqualTo("auto");
    }

    @Test
    @DisplayName("采样参数优先级：单次请求 > 配置默认值")
    void requestOptionsOverrideConfig() {
        ChatOptions options = ChatOptions.builder()
                .model("qwen-max")
                .temperature(0.9)
                .maxTokens(64)
                .topP(0.5)
                .stop("END")
                .toolChoice("none")
                .extra("enable_thinking", false)
                .build();

        JsonNode body = OpenAiProtocol.buildBody(CONFIG, ChatRequest.builder()
                .messages(List.of(Message.user("hi")))
                .options(options)
                .build(), false);

        assertThat(body.path("model").asString()).isEqualTo("qwen-max");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.9);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(64);
        assertThat(body.path("top_p").asDouble()).isEqualTo(0.5);
        assertThat(body.path("stop").get(0).asString()).isEqualTo("END");
        // 厂商私有参数被原样透传
        assertThat(body.path("enable_thinking").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("未覆盖时使用配置里的默认采样参数")
    void fallsBackToConfigDefaults() {
        JsonNode body = OpenAiProtocol.buildBody(CONFIG, ChatRequest.builder()
                .messages(List.of(Message.user("hi")))
                .build(), false);

        assertThat(body.path("model").asString()).isEqualTo("qwen-plus");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(512);
    }

    @Test
    @DisplayName("流式请求带上 stream 与 stream_options")
    void setsStreamFlags() {
        ChatRequest request = ChatRequest.builder().messages(List.of(Message.user("hi"))).build();

        JsonNode streaming = OpenAiProtocol.buildBody(CONFIG, request, true);
        assertThat(streaming.path("stream").asBoolean()).isTrue();
        assertThat(streaming.path("stream_options").path("include_usage").asBoolean()).isTrue();

        JsonNode plain = OpenAiProtocol.buildBody(CONFIG, request, false);
        assertThat(plain.has("stream")).isFalse();
        assertThat(plain.has("stream_options")).isFalse();
    }

    // ------------------------------------------------------------------ 响应解码

    @Test
    @DisplayName("解析纯文本响应与 usage")
    void parsesTextResponse() {
        ObjectNode root = Json.obj();
        root.put("model", "qwen-plus");
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "stop");
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", "你好，我是 MyClaw");
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", 11);
        usage.put("completion_tokens", 7);
        usage.put("total_tokens", 18);

        ChatResponse response = OpenAiProtocol.parseResponse(root);

        assertThat(response.content()).isEqualTo("你好，我是 MyClaw");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.model()).isEqualTo("qwen-plus");
        assertThat(response.usage().promptTokens()).isEqualTo(11);
        assertThat(response.usage().completionTokens()).isEqualTo(7);
        assertThat(response.usage().totalTokens()).isEqualTo(18);
        assertThat(response.hasToolCalls()).isFalse();
    }

    @Test
    @DisplayName("解析工具调用响应")
    void parsesToolCallResponse() {
        ObjectNode root = Json.obj();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "tool_calls");
        ObjectNode message = choice.putObject("message");
        message.putNull("content");
        ObjectNode call = message.putArray("tool_calls").addObject();
        call.put("id", "call_abc");
        call.put("type", "function");
        ObjectNode function = call.putObject("function");
        function.put("name", "get_weather");
        function.put("arguments", "{\"city\":\"杭州\"}");

        ChatResponse response = OpenAiProtocol.parseResponse(root);

        assertThat(response.content()).isNull();
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls()).singleElement().satisfies(toolCall -> {
            assertThat(toolCall.id()).isEqualTo("call_abc");
            assertThat(toolCall.name()).isEqualTo("get_weather");
            assertThat(toolCall.arguments()).isEqualTo("{\"city\":\"杭州\"}");
        });
    }

    @Test
    @DisplayName("content 为分段数组时被拼成纯文本")
    void joinsArrayContent() {
        ObjectNode root = Json.obj();
        ObjectNode choice = root.putArray("choices").addObject();
        ObjectNode message = choice.putObject("message");
        var parts = message.putArray("content");
        parts.addObject().put("type", "text").put("text", "第一段");
        parts.addObject().put("type", "text").put("text", "第二段");

        assertThat(OpenAiProtocol.parseResponse(root).content()).isEqualTo("第一段第二段");
    }

    @Test
    @DisplayName("缺少 choices 时抛出 ModelException")
    void failsWhenChoicesMissing() {
        ObjectNode root = Json.obj();
        root.put("error", "something went wrong");

        assertThatThrownBy(() -> OpenAiProtocol.parseResponse(root))
                .isInstanceOf(ModelException.class)
                .hasMessageContaining("choices");
    }

    @Test
    @DisplayName("错误信息优先取厂商返回的 error.message")
    void extractsProviderErrorMessage() {
        String body = Json.write(Json.obj().set("error", Json.obj().put("message", "Invalid API key provided")));
        assertThat(OpenAiProtocol.describeError(401, body))
                .isEqualTo("HTTP 401: Invalid API key provided");

        assertThat(OpenAiProtocol.describeError(502, "<html>bad gateway</html>"))
                .isEqualTo("HTTP 502: <html>bad gateway</html>");

        assertThat(OpenAiProtocol.describeError(500, null)).isEqualTo("HTTP 500: ");
    }

    @Test
    @DisplayName("没有 usage 字段时返回空消耗而不是 NPE")
    void handlesMissingUsage() {
        assertThat(OpenAiProtocol.parseUsage(Json.obj())).isEqualTo(io.myclaw.core.message.ChatUsage.EMPTY);
        assertThat(OpenAiProtocol.parseToolCalls(Json.obj())).isEmpty();
        assertThat(OpenAiProtocol.readContent(null)).isNull();
    }

    @Test
    @DisplayName("从环境变量推断的配置有合理默认值")
    void configDefaults() {
        OpenAiConfig config = OpenAiConfig.builder().apiKey("k").build();

        assertThat(config.baseUrl()).isEqualTo(OpenAiConfig.DEFAULT_BASE_URL);
        assertThat(config.model()).isEqualTo(OpenAiConfig.DEFAULT_MODEL);
        assertThat(config.chatCompletionsUrl()).isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(config.hasApiKey()).isTrue();
    }

    @Test
    @DisplayName("baseUrl 结尾多余的斜杠被去掉，Key 在描述中被脱敏")
    void normalizesBaseUrlAndMasksKey() {
        OpenAiConfig config = OpenAiConfig.builder().baseUrl("https://x.com/v1///").apiKey("sk-1234567890").build();

        assertThat(config.baseUrl()).isEqualTo("https://x.com/v1");
        assertThat(config.describe()).contains("sk-123").doesNotContain("4567890");
    }

    @Test
    @DisplayName("DashScope 预设指向通义千问兼容端点")
    void dashscopePreset() {
        OpenAiConfig config = OpenAiConfig.dashscope("sk-x").build();

        assertThat(config.baseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertThat(config.model()).isEqualTo("qwen-plus");
    }

    @Test
    @DisplayName("透传 Map 类型的厂商私有参数")
    void passesThroughMapExtra() {
        ChatOptions options = ChatOptions.builder()
                .extra("extra_body", Map.of("enable_search", true))
                .build();

        JsonNode body = OpenAiProtocol.buildBody(CONFIG, ChatRequest.builder()
                .messages(List.of(Message.user("hi")))
                .options(options)
                .build(), false);

        assertThat(body.path("extra_body").path("enable_search").asBoolean()).isTrue();
    }
}
