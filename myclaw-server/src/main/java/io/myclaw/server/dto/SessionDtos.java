package io.myclaw.server.dto;

import io.myclaw.server.service.AttachmentService;

import java.time.Instant;
import java.util.List;

public final class SessionDtos {
    private SessionDtos() {
    }

    public record SessionSummary(String id, String title, Instant createdAt, Instant updatedAt, long messageCount) {
    }

    public record SessionDetail(String id, String title, Instant createdAt, Instant updatedAt,
                                List<StoredMessage> messages) {
    }

    public record StoredMessage(Long id, String role, String content, String status, Instant createdAt,
                                Integer iterations, Integer toolCalls, Integer totalTokens, Long durationMillis,
                                List<AttachmentService.View> attachments, String feedback) {
    }

    public record CreateSession(String id, String title) {
    }

    public record RenameSession(String title) {
    }
}