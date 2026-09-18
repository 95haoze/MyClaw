package io.myclaw.server.dto;

import java.util.List;

public record ChatRequest(
        // 代表本次回答任务
        String requestId,
        // 代表整个对话
        String sessionId,
        List<ChatMessage> messages,
        List<String> attachmentIds
) {
}
