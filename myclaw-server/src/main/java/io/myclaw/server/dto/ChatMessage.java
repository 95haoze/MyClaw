package io.myclaw.server.dto;

public record ChatMessage(
        String role,
        String content
) {}
