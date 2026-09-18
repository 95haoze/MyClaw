package io.myclaw.core.message;

/**
 * 对话消息的角色，对应 OpenAI Chat Completions 协议中的 {@code role} 字段。
 */
public enum Role {

    /** 系统提示词，位于对话最前，用于设定 Agent 的人设与约束。 */
    SYSTEM("system"),

    /** 用户输入。 */
    USER("user"),

    /** 模型回复（可能只包含工具调用请求）。 */
    ASSISTANT("assistant"),

    /** 工具执行结果，回填给模型。 */
    TOOL("tool");

    private final String wireName;

    Role(String wireName) {
        this.wireName = wireName;
    }

    /** 协议中的字面量名称，例如 {@code "assistant"}。 */
    public String wireName() {
        return wireName;
    }

    /** 从协议字面量解析角色，大小写不敏感。 */
    public static Role fromWireName(String name) {
        for (Role role : values()) {
            if (role.wireName.equalsIgnoreCase(name)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的消息角色: " + name);
    }
}
