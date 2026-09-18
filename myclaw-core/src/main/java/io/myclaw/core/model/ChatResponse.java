package io.myclaw.core.model;

import io.myclaw.core.message.ChatUsage;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.ToolCall;

import java.util.List;

/**
 * 一次模型调用的返回结果。
 *
 * @param message      模型产出的消息
 * @param usage        token 消耗
 * @param model        实际使用的模型名
 * @param finishReason 结束原因：{@code stop} / {@code tool_calls} / {@code length} 等
 */
public record ChatResponse(Message message, ChatUsage usage, String model, String finishReason) {

    public ChatResponse {
        usage = usage == null ? ChatUsage.EMPTY : usage;
    }

    public static ChatResponse of(Message message) {
        return new ChatResponse(message, ChatUsage.EMPTY, null, "stop");
    }

    public static ChatResponse text(String content) {
        return of(Message.assistant(content));
    }

    /** 模型回复的文本内容，可能为 {@code null}。 */
    public String content() {
        return message.content();
    }

    /** 模型请求的工具调用，可能为空列表。 */
    public List<ToolCall> toolCalls() {
        return message.toolCalls();
    }

    /** 本次回复是否要求调用工具。 */
    public boolean hasToolCalls() {
        return message.hasToolCalls();
    }
}
