package io.myclaw.core.exception;

/**
 * 调用模型失败时抛出，例如网络错误、鉴权失败、响应格式不符合预期、触发限流重试仍失败等。
 */
public class ModelException extends MyClawException {

    private final int statusCode;

    public ModelException(String message) {
        this(message, -1, null);
    }

    public ModelException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public ModelException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP 状态码；非 HTTP 错误时为 {@code -1}。 */
    public int statusCode() {
        return statusCode;
    }
}
