package io.myclaw.core.tool;

/**
 * 单次工具调用的执行结果。
 *
 * @param toolCallId     对应的 {@link io.myclaw.core.message.ToolCall#id()}
 * @param toolName       工具名
 * @param content        回填给模型的文本
 * @param error          是否为失败结果
 * @param durationMillis 执行耗时（毫秒）
 */
public record ToolResult(String toolCallId, String toolName, String content, boolean error, long durationMillis) {

    public static ToolResult success(String toolCallId, String toolName, String content, long durationMillis) {
        return new ToolResult(toolCallId, toolName, content, false, durationMillis);
    }

    public static ToolResult failure(String toolCallId, String toolName, String message, long durationMillis) {
        return new ToolResult(toolCallId, toolName, message, true, durationMillis);
    }

    /** 转为回填给模型的消息内容。失败时加上 {@code ERROR:} 前缀，帮助模型识别并纠正。 */
    public String toModelContent() {
        return error ? "ERROR: " + content : content;
    }
}
