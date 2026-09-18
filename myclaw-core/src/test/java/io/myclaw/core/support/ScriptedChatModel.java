package io.myclaw.core.support;

import io.myclaw.core.message.Message;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatModel;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.model.StreamListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * 可脚本化的测试模型：按预先编排的顺序返回响应，并记录收到的全部请求。
 *
 * <p>有了它，Agent 循环的测试完全不需要联网、不需要 API Key，且结果是确定性的。
 *
 * <pre>{@code
 * ScriptedChatModel model = ScriptedChatModel.create()
 *         .thenCallTool("add", "{\"a\":1,\"b\":2}")   // 第 1 轮：要求调用工具
 *         .thenReply("答案是 3");                       // 第 2 轮：给出最终答案
 * }</pre>
 */
public final class ScriptedChatModel implements ChatModel {

    private final Deque<Function<ChatRequest, ChatResponse>> script = new ArrayDeque<>();
    private final List<ChatRequest> requests = new ArrayList<>();
    private final String name;

    /** 开启后，{@link #stream} 会把文本按字符逐个吐出，用来验证真实的流式路径。 */
    private boolean chunkedStreaming;

    private ScriptedChatModel(String name) {
        this.name = name;
    }

    public static ScriptedChatModel create() {
        return new ScriptedChatModel("scripted");
    }

    public static ScriptedChatModel create(String name) {
        return new ScriptedChatModel(name);
    }

    /** 开启按字符切分的模拟流式输出。 */
    public ScriptedChatModel chunkedStreaming() {
        this.chunkedStreaming = true;
        return this;
    }

    /** 排入一个纯文本回复。 */
    public ScriptedChatModel thenReply(String text) {
        return then(request -> ChatResponse.text(text));
    }

    /** 排入一个工具调用请求。 */
    public ScriptedChatModel thenCallTools(ToolCall... calls) {
        return then(request -> ChatResponse.of(Message.assistant(null, List.of(calls))));
    }

    /** 排入一个指定工具名的调用请求。 */
    public ScriptedChatModel thenCallTool(String toolName, String argumentsJson) {
        return thenCallTools(ToolCall.of(toolName, argumentsJson));
    }

    /** 排入一个自定义处理逻辑，可以读取 {@link ChatRequest} 动态决定响应。 */
    public ScriptedChatModel then(Function<ChatRequest, ChatResponse> handler) {
        script.add(handler);
        return this;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        requests.add(request);
        Function<ChatRequest, ChatResponse> next = script.poll();
        if (next == null) {
            throw new IllegalStateException("ScriptedChatModel 脚本已耗尽：第 " + requests.size() + " 次调用没有可用的响应");
        }
        return next.apply(request);
    }

    @Override
    public ChatResponse stream(ChatRequest request, StreamListener listener) {
        if (!chunkedStreaming) {
            return ChatModel.super.stream(request, listener);
        }
        listener.onStart();
        ChatResponse response = chat(request);
        String content = response.content();
        if (content != null) {
            for (String chunk : content.split("")) {
                listener.onText(chunk);
            }
        }
        listener.onComplete(response);
        return response;
    }

    @Override
    public String name() {
        return name;
    }

    /** 收到的全部请求，按时间顺序。 */
    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    /** 最后一次收到的请求。 */
    public ChatRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("尚未收到任何请求");
        }
        return requests.get(requests.size() - 1);
    }

    /** 被调用的次数。 */
    public int callCount() {
        return requests.size();
    }
}
