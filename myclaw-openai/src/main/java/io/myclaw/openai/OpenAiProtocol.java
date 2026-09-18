package io.myclaw.openai;

import io.myclaw.core.json.Json;
import io.myclaw.core.message.ChatUsage;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatOptions;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Chat Completions 协议的编解码层。
 *
 * <p>把 MyClaw 内核模型（{@link Message} / {@link ChatRequest}）翻译成协议 JSON，
 * 以及反向解析。所有厂商差异（例如 content 既可能是字符串也可能是分段数组）
 * 都收敛在这里，保证 {@link OpenAiChatModel} 只管 HTTP。
 */
final class OpenAiProtocol {

    private OpenAiProtocol() {
    }

    // ------------------------------------------------------------------ 请求编码

    /** 构造 {@code POST /chat/completions} 的请求体。 */
    static ObjectNode buildBody(OpenAiConfig config, ChatRequest request, boolean stream) {
        ChatOptions options = request.options();

        String model = options.model() != null ? options.model() : config.model();
        Double temperature = options.temperature() != null ? options.temperature() : config.temperature();
        Integer maxTokens = options.maxTokens() != null ? options.maxTokens() : config.maxTokens();
        Double topP = options.topP() != null ? options.topP() : config.topP();

        ObjectNode body = Json.obj();
        body.put("model", model);

        ArrayNode messages = Json.arr();
        for (Message message : request.messages()) {
            messages.add(messageToJson(message));
        }
        body.set("messages", messages);

        if (!request.tools().isEmpty()) {
            ArrayNode tools = Json.arr();
            for (ToolDefinition definition : request.tools()) {
                tools.add(toolToJson(definition));
            }
            body.set("tools", tools);
            body.put("tool_choice", options.toolChoice() != null ? options.toolChoice() : "auto");
        }

        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        if (topP != null) {
            body.put("top_p", topP);
        }
        if (!options.stop().isEmpty()) {
            ArrayNode stop = Json.arr();
            options.stop().forEach(stop::add);
            body.set("stop", stop);
        }

        if (stream) {
            body.put("stream", true);
            if (config.includeUsageInStream()) {
                ObjectNode streamOptions = Json.obj();
                streamOptions.put("include_usage", true);
                body.set("stream_options", streamOptions);
            }
        }

        // 厂商私有参数原样透传
        options.extra().forEach((key, value) -> body.set(key, Json.toNode(value)));

        return body;
    }

    /** 单条消息 -> 协议 JSON。 */
    static ObjectNode messageToJson(Message message) {
        ObjectNode node = Json.obj();
        switch (message.role()) {
            case SYSTEM, USER -> {
                node.put("role", message.role().wireName());
                node.put("content", message.content() == null ? "" : message.content());
            }
            case ASSISTANT -> {
                node.put("role", "assistant");
                // 带 tool_calls 时 content 允许为空串（比 null 兼容性更好，DashScope 亦接受）
                node.put("content", message.content() == null ? "" : message.content());
                if (message.hasToolCalls()) {
                    ArrayNode calls = Json.arr();
                    for (ToolCall call : message.toolCalls()) {
                        ObjectNode callNode = Json.obj();
                        callNode.put("id", call.id());
                        callNode.put("type", "function");
                        ObjectNode function = Json.obj();
                        function.put("name", call.name());
                        function.put("arguments", call.arguments());
                        callNode.set("function", function);
                        calls.add(callNode);
                    }
                    node.set("tool_calls", calls);
                }
            }
            case TOOL -> {
                node.put("role", "tool");
                node.put("tool_call_id", message.toolCallId());
                node.put("content", message.content() == null ? "" : message.content());
            }
        }
        return node;
    }

    /** 工具定义 -> 协议 JSON。 */
    static ObjectNode toolToJson(ToolDefinition definition) {
        ObjectNode tool = Json.obj();
        tool.put("type", "function");
        ObjectNode function = Json.obj();
        function.put("name", definition.name());
        function.put("description", definition.description());
        function.set("parameters", definition.parameters());
        tool.set("function", function);
        return tool;
    }

    // ------------------------------------------------------------------ 响应解码

    /** 解析一次非流式响应。 */
    static ChatResponse parseResponse(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new io.myclaw.core.exception.ModelException(
                    "模型响应中缺少 choices 字段，原始响应: " + abbreviate(Json.write(root)));
        }
        JsonNode choice = choices.get(0);
        JsonNode message = choice.path("message");

        String content = readContent(message.get("content"));
        List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));

        return new ChatResponse(
                Message.assistant(content, toolCalls),
                parseUsage(root.path("usage")),
                Json.text(root, "model"),
                Json.text(choice, "finish_reason"));
    }

    /**
     * 读取 message.content。
     *
     * <p>兼容两种形态：普通字符串，以及部分厂商返回的分段数组
     * （{@code [{"type":"text","text":"..."}]}）。
     */
    static String readContent(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < node.size(); i++) {
                JsonNode part = node.get(i);
                if (part == null || part.isNull()) {
                    continue;
                }
                if (part.isString()) {
                    sb.append(part.asString());
                } else if (part.has("text")) {
                    sb.append(part.path("text").asString());
                }
            }
            return sb.toString();
        }
        return node.asString();
    }

    /** 解析 {@code message.tool_calls}。 */
    static List<ToolCall> parseToolCalls(JsonNode node) {
        List<ToolCall> calls = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return calls;
        }
        for (int i = 0; i < node.size(); i++) {
            JsonNode call = node.get(i);
            JsonNode function = call.path("function");
            String name = Json.text(function, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            calls.add(new ToolCall(Json.text(call, "id"), name, Json.text(function, "arguments")));
        }
        return calls;
    }

    /** 解析 {@code usage}。 */
    static ChatUsage parseUsage(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull() || !usage.isObject()) {
            return ChatUsage.EMPTY;
        }
        return new ChatUsage(
                readInt(usage.get("prompt_tokens")),
                readInt(usage.get("completion_tokens")),
                readInt(usage.get("total_tokens")));
    }

    private static Integer readInt(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asInt();
    }

    /** 把非 2xx 响应整理成人类可读、且对模型友好的错误信息。 */
    static String describeError(int status, String body) {
        String detail = body == null ? "" : body.strip();
        try {
            JsonNode root = Json.parse(detail);
            String message = Json.text(root.path("error"), "message");
            if (message == null) {
                message = Json.text(root, "message");
            }
            if (message != null && !message.isBlank()) {
                detail = message;
            }
        } catch (RuntimeException ignored) {
            // body 不是 JSON，保持原样
        }
        return "HTTP " + status + ": " + abbreviate(detail);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 1000 ? text : text.substring(0, 1000) + "...";
    }
}
