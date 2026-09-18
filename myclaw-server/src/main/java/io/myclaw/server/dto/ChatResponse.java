package io.myclaw.server.dto;

import io.myclaw.core.agent.AgentResponse;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.tool.ToolResult;

import java.util.List;

public record ChatResponse(String content, int iterations, int toolCalls, int totalTokens,
                           long durationMillis, List<ToolExecution> tools, Long messageId) {

    public record ToolExecution(String id, String name, String arguments, String status, long durationMillis,
                                String result) {
    }

    public static ChatResponse from(AgentResponse response) {
        return new ChatResponse(response.content(), response.iterations(), response.toolResults().size(),
                response.usage().totalTokens(), response.durationMillis(),
                response.steps().stream().filter(io.myclaw.core.agent.AgentStep.ToolUse.class::isInstance)
                        .map(io.myclaw.core.agent.AgentStep.ToolUse.class::cast)
                        .flatMap(step -> step.calls().stream().map(call -> toToolExecution(call, step.results()))).toList(), null);
    }

    public ChatResponse withMessageId(Long id) {
        return new ChatResponse(content, iterations, toolCalls, totalTokens, durationMillis, tools, id);
    }

    private static ToolExecution toToolExecution(ToolCall call, List<ToolResult> results) {
        ToolResult result = results.stream().filter(item -> item.toolCallId().equals(call.id())).findFirst().orElse(null);
        return new ToolExecution(call.id(), call.name(), call.arguments(),
                result == null ? "unknown" : result.error() ? "failed" : "completed",
                result == null ? 0 : result.durationMillis(), result == null ? "" : result.content());
    }
}