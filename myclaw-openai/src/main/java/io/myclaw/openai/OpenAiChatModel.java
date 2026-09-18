package io.myclaw.openai;

import io.myclaw.core.exception.ModelException;
import io.myclaw.core.json.Json;
import io.myclaw.core.message.ChatUsage;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatModel;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.model.StreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 <b>OpenAI Chat Completions 兼容协议</b>的 {@link ChatModel} 实现。
 *
 * <p>只用 JDK 自带的 {@link HttpClient}，不引入任何第三方 HTTP 或 JSON 库，
 * 因此同一份代码可以无缝对接 OpenAI、通义千问（DashScope 兼容模式）、DeepSeek、Ollama 等。
 *
 * <p>用法：
 * <pre>{@code
 * // 通义千问
 * OpenAiChatModel model = OpenAiChatModel.of(OpenAiConfig.dashscope(apiKey).build());
 *
 * // 或者从环境变量自动推断
 * OpenAiChatModel model = OpenAiChatModel.of(OpenAiConfig.fromEnv());
 *
 * Agent agent = Agent.builder("assistant").model(model).tools(registry).build();
 * System.out.println(agent.run("杭州现在几点？").content());
 * }</pre>
 *
 * <p>已实现的能力：
 * <ul>
 *   <li>function calling（工具调用）</li>
 *   <li>SSE 真流式输出，工具调用分片自动聚合</li>
 *   <li>对 429 / 5xx 做指数退避重试（流式只在尚未输出任何内容时重试）</li>
 *   <li>错误信息整合厂商返回的 {@code error.message}，便于排查</li>
 * </ul>
 */
public final class OpenAiChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatModel.class);

    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";

    private final OpenAiConfig config;
    private final HttpClient httpClient;

    /** 用默认 HttpClient 创建。 */
    public OpenAiChatModel(OpenAiConfig config) {
        this(config, null);
    }

    /**
     * @param httpClient 自定义 HttpClient（便于测试注入、统一连接池）；传 {@code null} 使用默认实现
     */
    public OpenAiChatModel(OpenAiConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config 不能为空");
        if (!config.hasApiKey()) {
            log.warn("API Key 未配置，模型调用将会失败。{}", config.describe());
        }
        this.httpClient = httpClient != null ? httpClient : defaultHttpClient();
    }

    public static OpenAiChatModel of(OpenAiConfig config) {
        return new OpenAiChatModel(config);
    }

    public OpenAiConfig config() {
        return config;
    }

    @Override
    public String name() {
        return "openai:" + config.model();
    }

    // ------------------------------- 同步调用 -----------------------------------

    @Override
    public ChatResponse chat(ChatRequest request) {
        ObjectNode body = OpenAiProtocol.buildBody(config, request, false);
        String payload = Json.write(body);
        log.debug("POST {} model={} messages={} tools={}",
                config.chatCompletionsUrl(), body.path("model").asString(),
                request.messages().size(), request.tools().size());

        HttpResponse<String> response = execute(
                buildRequest(payload, false), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ChatResponse parsed = OpenAiProtocol.parseResponse(Json.parse(response.body()));
        log.debug("模型返回: finishReason={}, usage=[{}], toolCalls={}",
                parsed.finishReason(), parsed.usage(),
                parsed.toolCalls().stream().map(ToolCall::name).toList());
        return parsed;
    }

    // --------------------------------- 流式调用 ---------------------------------

    @Override
    public ChatResponse stream(ChatRequest request, StreamListener listener) {
        ObjectNode body = OpenAiProtocol.buildBody(config, request, true);
        String payload = Json.write(body);

        listener.onStart();
        try {
            HttpResponse<InputStream> response = execute(
                    buildRequest(payload, true), HttpResponse.BodyHandlers.ofInputStream());
            InputStream stream = response.body();
            listener.onCancellable(() -> closeQuietly(stream));
            return consumeSse(stream, listener);
        } catch (RuntimeException e) {
            listener.onError(e);
            throw e;
        }
    }

    /**
     * 消费 SSE 流。
     *
     * <p>工具调用的参数是<b>分片</b>下发的（同一 index 会连续来很多个 delta），
     * 必须按 index 聚合后再交给 Agent，否则会拿到被截断的 JSON。
     */
    private ChatResponse consumeSse(InputStream stream, StreamListener listener) {
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCallAccumulator> accumulators = new LinkedHashMap<>();
        ChatUsage usage = ChatUsage.EMPTY;
        String model = config.model();
        String finishReason = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith(SSE_DATA_PREFIX)) {
                    continue;
                }
                String data = line.substring(SSE_DATA_PREFIX.length()).strip();
                if (data.isEmpty()) {
                    continue;
                }
                if (SSE_DONE.equals(data)) {
                    break;
                }

                JsonNode chunk = Json.parse(data);
                String chunkModel = Json.text(chunk, "model");
                if (chunkModel != null) {
                    model = chunkModel;
                }
                JsonNode usageNode = chunk.path("usage");
                if (usageNode.isObject()) {
                    usage = OpenAiProtocol.parseUsage(usageNode);
                }

                JsonNode choices = chunk.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    continue;
                }
                JsonNode choice = choices.get(0);
                String chunkFinish = Json.text(choice, "finish_reason");
                if (chunkFinish != null) {
                    finishReason = chunkFinish;
                }

                JsonNode delta = choice.path("delta");
                String text = OpenAiProtocol.readContent(delta.get("content"));
                if (text != null && !text.isEmpty()) {
                    content.append(text);
                    listener.onText(text);
                }

                JsonNode toolCalls = delta.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (int i = 0; i < toolCalls.size(); i++) {
                        accumulate(toolCalls.get(i), i, accumulators);
                    }
                }
            }
        } catch (IOException e) {
            throw new ModelException("读取流式响应失败: " + e.getMessage(), -1, e);
        }

        List<ToolCall> calls = accumulators.values().stream()
                .filter(ToolCallAccumulator::isComplete)
                .map(ToolCallAccumulator::toToolCall)
                .toList();

        ChatResponse response = new ChatResponse(
                Message.assistant(content.isEmpty() ? null : content.toString(), calls),
                usage, model, finishReason);
        listener.onComplete(response);
        return response;
    }

    private static void accumulate(JsonNode delta, int fallbackIndex, Map<Integer, ToolCallAccumulator> accumulators) {
        int index = delta.has("index") ? delta.path("index").asInt() : fallbackIndex;
        ToolCallAccumulator accumulator = accumulators.computeIfAbsent(index, key -> new ToolCallAccumulator());

        String id = Json.text(delta, "id");
        if (id != null && !id.isBlank()) {
            accumulator.id = id;
        }
        JsonNode function = delta.path("function");
        String name = Json.text(function, "name");
        if (name != null) {
            accumulator.name.append(name);
        }
        String arguments = Json.text(function, "arguments");
        if (arguments != null) {
            accumulator.arguments.append(arguments);
        }
    }

    // ------------------------------------------------------------------ HTTP 细节

    private HttpRequest buildRequest(String payload, boolean stream) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.chatCompletionsUrl()))
                .timeout(config.timeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

        if (config.hasApiKey()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        if (config.organization() != null && !config.organization().isBlank()) {
            builder.header("OpenAI-Organization", config.organization());
        }
        config.extraHeaders().forEach(builder::header);
        return builder.build();
    }

    /**
     * 带指数退避重试的请求执行。仅对 429 与 5xx 重试；其它状态码立即抛出。
     */
    private <T> HttpResponse<T> execute(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        int attempts = config.maxRetries() + 1;
        ModelException lastError = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                sleepBackoff(attempt - 1);
            }
            try {
                HttpResponse<T> response = httpClient.send(request, handler);
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response;
                }
                String detail = OpenAiProtocol.describeError(status, readBody(response));
                lastError = new ModelException(detail, status, null);
                if (!isRetryable(status)) {
                    throw lastError;
                }
                log.warn("模型调用失败（{}/{}）: {}", attempt, attempts, detail);
            } catch (IOException e) {
                lastError = new ModelException("调用模型失败: " + e.getMessage(), -1, e);
                if (attempt == attempts) {
                    throw lastError;
                }
                log.warn("模型调用出现 IO 异常（{}/{}）: {}", attempt, attempts, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModelException("调用模型被中断", -1, e);
            }
        }
        throw lastError != null ? lastError : new ModelException("调用模型失败：未知原因");
    }

    private void sleepBackoff(int round) {
        long millis = Math.min(500L << (round - 1), 8000L);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("重试等待被中断", -1, e);
        }
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    /** 把响应体读成字符串（用于错误信息），并关闭流式响应体避免连接泄漏。 */
    private static String readBody(HttpResponse<?> response) {
        Object body = response.body();
        if (body instanceof String text) {
            return text;
        }
        if (body instanceof InputStream stream) {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "<无法读取响应体: " + e.getMessage() + ">";
            }
        }
        return String.valueOf(body);
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 取消操作只负责尽快释放连接，关闭失败由读取线程处理。
        }
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                // 部分兼容端点对 HTTP/2 支持不完整，统一走 HTTP/1.1 更稳
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String toString() {
        return "OpenAiChatModel(" + config.describe() + ")";
    }

    /** 按 index 聚合流式返回的工具调用分片。 */
    private static final class ToolCallAccumulator {
        private String id;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();

        boolean isComplete() {
            return !name.isEmpty();
        }

        ToolCall toToolCall() {
            return new ToolCall(id, name.toString(), arguments.toString());
        }
    }
}
