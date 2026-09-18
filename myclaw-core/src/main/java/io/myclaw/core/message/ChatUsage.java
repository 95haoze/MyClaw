package io.myclaw.core.message;

/**
 * 一次模型调用的 token 消耗。
 *
 * @param promptTokens     输入 token 数
 * @param completionTokens 输出 token 数
 * @param totalTokens      合计 token 数
 */
public record ChatUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    public static final ChatUsage EMPTY = new ChatUsage(0, 0, 0);

    public ChatUsage {
        promptTokens = promptTokens == null ? 0 : promptTokens;
        completionTokens = completionTokens == null ? 0 : completionTokens;
        totalTokens = totalTokens == null ? 0 : totalTokens;
    }

    /** 累加两次调用的消耗，用于统计整个 Agent 循环的开销。 */
    public ChatUsage plus(ChatUsage other) {
        if (other == null) {
            return this;
        }
        return new ChatUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens);
    }

    @Override
    public String toString() {
        return "prompt=" + promptTokens + ", completion=" + completionTokens + ", total=" + totalTokens;
    }
}
