package io.myclaw.core.agent;

import io.myclaw.core.message.ChatUsage;
import io.myclaw.core.tool.ToolResult;

import java.util.List;

/**
 * 一次 Agent 调用的完整结果。
 *
 * @param content        最终的自然语言答案
 * @param steps          循环中的每一步（工具调用 + 最终答案），可用于审计与调试
 * @param usage          整轮对话累计的 token 消耗
 * @param iterations     实际经历的模型调用轮数
 * @param durationMillis 总耗时（毫秒）
 */
public record AgentResponse(
        String content,
        List<AgentStep> steps,
        ChatUsage usage,
        int iterations,
        long durationMillis) {

    public AgentResponse {
        content = content == null ? "" : content;
        steps = steps == null ? List.of() : List.copyOf(steps);
        usage = usage == null ? ChatUsage.EMPTY : usage;
    }

    /** 本次调用中是否真正使用过工具。 */
    public boolean usedTools() {
        return steps.stream().anyMatch(step -> step instanceof AgentStep.ToolUse);
    }

    /** 摊平所有工具执行结果。 */
    public List<ToolResult> toolResults() {
        return steps.stream()
                .filter(AgentStep.ToolUse.class::isInstance)
                .map(AgentStep.ToolUse.class::cast)
                .flatMap(step -> step.results().stream())
                .toList();
    }

    @Override
    public String toString() {
        return "AgentResponse(iterations=" + iterations
                + ", usedTools=" + usedTools()
                + ", usage=[" + usage + "]"
                + ", " + durationMillis + "ms)";
    }
}
