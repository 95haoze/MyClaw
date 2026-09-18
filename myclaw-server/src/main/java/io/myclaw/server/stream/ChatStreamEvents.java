package io.myclaw.server.stream;

public final class ChatStreamEvents {

    private ChatStreamEvents() {
    }

    public record Start(String requestId, Long userMessageId) {
    }

    public record Status(String code, String label, int iteration) {
    }

    public record Tool(String id, String name, String arguments, String status, long durationMillis, String result) {
    }

    public record Delta(String content) {
    }

    public record Error(String code, String message, boolean retryable) {
    }
}
