package io.myclaw.core.message;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一条对话消息，是内核与模型之间唯一的数据交换单位。
 *
 * <p>不同角色使用的字段不同：
 * <ul>
 *   <li>{@link Role#SYSTEM} / {@link Role#USER}：只使用 {@code content}</li>
 *   <li>{@link Role#ASSISTANT}：使用 {@code content}（可能为空）与 {@code toolCalls}</li>
 *   <li>{@link Role#TOOL}：使用 {@code toolCallId}、{@code name} 与 {@code content}（工具返回值）</li>
 * </ul>
 *
 * @param role       消息角色
 * @param content    文本内容，可为 {@code null}
 * @param toolCalls  模型请求的工具调用列表，非 null
 * @param toolCallId 工具结果消息所对应的调用 id
 * @param name       工具结果消息所对应的工具名
 */
public record Message(Role role, String content, List<ToolCall> toolCalls, String toolCallId, String name) {

    public Message {
        Objects.requireNonNull(role, "role 不能为空");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 系统提示词。 */
    public static Message system(String content) {
        return new Message(Role.SYSTEM, content, List.of(), null, null);
    }

    /** 用户输入。 */
    public static Message user(String content) {
        return new Message(Role.USER, content, List.of(), null, null);
    }

    /** 纯文本的模型回复。 */
    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content, List.of(), null, null);
    }

    /** 携带工具调用请求的模型回复。 */
    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(Role.ASSISTANT, content, toolCalls, null, null);
    }

    /** 工具执行结果，回填给模型继续推理。 */
    public static Message tool(String toolCallId, String name, String content) {
        return new Message(Role.TOOL, content, List.of(), toolCallId, name);
    }

    /** 是否包含工具调用请求。 */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** 以 {@link Optional} 形式返回文本内容。 */
    public Optional<String> contentOpt() {
        return Optional.ofNullable(content);
    }

    /** 文本内容是否非空白。 */
    public boolean hasText() {
        return content != null && !content.isBlank();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(role.wireName());
        if (name != null) {
            sb.append('(').append(name).append(')');
        }
        sb.append(": ");
        if (content != null) {
            sb.append(content.length() > 120 ? content.substring(0, 120) + "..." : content);
        }
        if (hasToolCalls()) {
            sb.append(" [tool_calls=").append(toolCalls.stream().map(ToolCall::name).toList()).append(']');
        }
        return sb.toString();
    }
}
