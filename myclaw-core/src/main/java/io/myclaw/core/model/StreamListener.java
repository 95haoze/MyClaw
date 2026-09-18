package io.myclaw.core.model;

import io.myclaw.core.message.ToolCall;
import io.myclaw.core.tool.ToolResult;

import java.util.List;

/**
 * 流式输出的回调监听器。
 *
 * <p>实现类只需要覆盖关心的方法。约定：一次流式调用中 {@link #onStart()} 最先触发，
 * 随后是零到多次 {@link #onText(String)}，最后以 {@link #onComplete(ChatResponse)} 或
 * {@link #onError(Throwable)} 结束。
 */
public interface StreamListener {

    /** 流开始。 */
    default void onStart() {
    }

    /**
     * 注册底层流的取消动作。调用方取消任务时可主动关闭网络流，
     * 避免线程一直阻塞在等待下一段数据。
     */
    default void onCancellable(Runnable cancelAction) {
    }

    /** 调用方是否已经取消本次流式执行。 */
    default boolean isCancelled() {
        return false;
    }

    /** Reports that the agent is requesting the model for an iteration. */
    default void onModelRequest(int iteration) {
    }

    /** Reports tool calls immediately before they are executed. */
    default void onToolsStart(int iteration, List<ToolCall> calls) {
    }

    /** Reports the actual result of a completed tool call. */
    default void onToolComplete(int iteration, ToolCall call, ToolResult result) {
    }

    /** Receives one increment of generated answer text. */
    default void onText(String delta) {
    }

    /** 流正常结束，携带聚合后的完整响应。 */
    default void onComplete(ChatResponse response) {
    }

    /** 流异常结束。 */
    default void onError(Throwable error) {
    }

    /** 便捷工厂：只关心文本增量。 */
    static StreamListener onText(java.util.function.Consumer<String> consumer) {
        return new StreamListener() {
            @Override
            public void onText(String delta) {
                consumer.accept(delta);
            }
        };
    }

    /** 把增量文本实时打印到标准输出。 */
    static StreamListener printing(java.io.PrintStream out) {
        return new StreamListener() {
            @Override
            public void onText(String delta) {
                out.print(delta);
                out.flush();
            }
        };
    }
}
