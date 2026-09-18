package io.myclaw.server.dto;

public record ApiResponse<T>(String code, String message, T data, boolean retryable) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, false);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(String code, String message, boolean retryable) {
        return new ApiResponse<>(code, message, null, retryable);
    }
}
