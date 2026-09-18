package io.myclaw.server.error;

public record ApiError(String code, String message, boolean retryable) {
}