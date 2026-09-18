package io.myclaw.core.model;

/**
 * 模型能力的统一抽象 —— 整个框架唯一与"具体是哪家模型"耦合的接口。
 *
 * <p>任何 provider（OpenAI、通义千问、DeepSeek、Ollama、Claude……）只要实现这个接口，
 * 就能接入 MyClaw 的 Agent 循环。框架自带的实现在 {@code myclaw-openai} 模块。
 *
 * <p>实现要点：
 * <ul>
 *   <li>{@link #chat(ChatRequest)} 是唯一的必需方法，异常请包装为
 *       {@link io.myclaw.core.exception.ModelException}</li>
 *   <li>{@link #stream} 有默认实现（退化为一次性输出），不再支持的 provider 无需实现</li>
 * </ul>
 */
public interface ChatModel {

    /**
     * 发起一次同步对话补全。
     *
     * @param request 消息、工具定义与采样参数
     * @return 模型响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 发起一次流式对话补全，并在收到增量时回调 {@code listener}。
     *
     * <p>默认实现退化为调用 {@link #chat(ChatRequest)} 后一次性吐出全文，
     * 因此所有 provider 都天然支持流式（只是不一定是真流式）。
     *
     * @return 聚合后的完整响应
     */
    default ChatResponse stream(ChatRequest request, StreamListener listener) {
        listener.onStart();
        try {
            ChatResponse response = chat(request);
            if (response.content() != null && !response.content().isEmpty()) {
                listener.onText(response.content());
            }
            listener.onComplete(response);
            return response;
        } catch (RuntimeException e) {
            listener.onError(e);
            throw e;
        }
    }

    /** provider 名称，用于日志与可观测性。 */
    default String name() {
        return getClass().getSimpleName();
    }

    /** 该模型是否支持 function calling；返回 false 时 Agent 不会向它暴露工具。 */
    default boolean supportsToolCalling() {
        return true;
    }
}
