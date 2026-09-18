package io.myclaw.core.agent;

import io.myclaw.core.message.ChatUsage;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.tool.ToolResult;

import java.util.List;

/**
 * Agent 循环中的一步。
 *
 * <p>一个 {@link AgentResponse} 由若干步组成：零到多个 {@link ToolUse}，最后以一个
 * {@link Answer} 收尾。保留完整步骤是为了让调用方能够审计"Agent 到底干了什么"。
 */
public sealed interface AgentStep permits AgentStep.ToolUse, AgentStep.Answer {

    /** 第几轮（从 1 开始，一次模型调用算一轮）。 */
    int index();

    /** 一轮"模型要求调用工具 -> 工具返回结果"。 */
    record ToolUse(int index, List<ToolCall> calls, List<ToolResult> results) implements AgentStep {

        public ToolUse {
            calls = List.copyOf(calls);
            results = List.copyOf(results);
        }
    }

    /** 最后一轮：模型给出了自然语言答案。 */
    record Answer(int index, String text, ChatUsage usage) implements AgentStep {

        public Answer {
            text = text == null ? "" : text;
            usage = usage == null ? ChatUsage.EMPTY : usage;
        }
    }
}
