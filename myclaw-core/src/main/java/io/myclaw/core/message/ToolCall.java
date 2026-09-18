package io.myclaw.core.message;

import java.util.UUID;

/**
 * 模型发起的一次工具调用请求。
 *
 * @param id        调用唯一标识，回填工具结果时通过它做关联
 * @param name      工具名，对应 {@link io.myclaw.core.tool.ToolDefinition#name()}
 * @param arguments 参数的原始 JSON 字符串（由模型生成，可能不合法，需要工具侧校验）
 */
public record ToolCall(String id, String name, String arguments) {

    public ToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名不能为空");
        }
        if (id == null || id.isBlank()) {
            id = randomId();
        }
        if (arguments == null || arguments.isBlank()) {
            arguments = "{}";
        }
    }

    /** 创建一个自动生成 id 的工具调用。 */
    public static ToolCall of(String name, String argumentsJson) {
        return new ToolCall(randomId(), name, argumentsJson);
    }

    private static String randomId() {
        return "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
