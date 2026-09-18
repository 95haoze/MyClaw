package io.myclaw.core.exception;

/**
 * Agent 在达到 {@code maxIterations} 上限后仍未产出最终答案时抛出。
 *
 * <p>通常意味着模型陷入了"调用工具 -> 再调用工具"的循环，需要调整提示词或提高上限。
 */
public class MaxIterationsException extends MyClawException {

    private final int maxIterations;

    public MaxIterationsException(int maxIterations) {
        super("Agent 在 " + maxIterations + " 轮迭代内未给出最终答案，可能存在工具调用死循环");
        this.maxIterations = maxIterations;
    }

    public int maxIterations() {
        return maxIterations;
    }
}
