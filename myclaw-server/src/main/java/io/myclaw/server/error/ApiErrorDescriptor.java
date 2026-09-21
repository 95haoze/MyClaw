package io.myclaw.server.error;

import io.myclaw.core.exception.MaxIterationsException;
import io.myclaw.core.exception.EmptyModelResponseException;
import io.myclaw.core.exception.ModelException;
import io.myclaw.core.exception.ToolExecutionException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.RejectedExecutionException;

public record ApiErrorDescriptor(
        HttpStatus status,
        String code,
        String message,
        boolean retryable
) {
    public ApiError body() {
        return new ApiError(code, message, retryable);
    }

    public static ApiErrorDescriptor from(Throwable error) {
        Throwable cause = findKnownCause(error);

        if (cause instanceof RepeatSubmitException) {
            return new ApiErrorDescriptor(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "REPEAT_SUBMIT",
                    cause.getMessage(),
                    true
            );
        }        if (cause instanceof AuthenticationException) {
            return new ApiErrorDescriptor(
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_INVALID_CREDENTIALS",
                    "用户名或密码错误",
                    false
            );
        }
        if (cause instanceof MaxIterationsException) {
            return new ApiErrorDescriptor(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "AGENT_MAX_ITERATIONS",
                    "Agent did not finish within the configured iteration limit. Try simplifying the request.",
                    true
            );
        }
        if (cause instanceof ToolExecutionException toolError) {
            return new ApiErrorDescriptor(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "TOOL_EXECUTION_FAILED",
                    toolError.getMessage(),
                    true
            );
        }
        if (cause instanceof EmptyModelResponseException) {
            return new ApiErrorDescriptor(
                    HttpStatus.BAD_GATEWAY,
                    "MODEL_EMPTY_RESPONSE",
                    "模型未返回有效内容，请检查模型名称、服务地址和 API Key 后重试。",
                    true
            );
        }
        if (cause instanceof ModelException modelError) {
            if (modelError.statusCode() == 401 || modelError.statusCode() == 403) {
                return new ApiErrorDescriptor(
                        HttpStatus.BAD_GATEWAY,
                        "MODEL_AUTH_FAILED",
                        "模型服务鉴权失败，请检查 API Key 是否正确或已失效。",
                        false
                );
            }
            if (modelError.statusCode() == 429) {
                return new ApiErrorDescriptor(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "MODEL_RATE_LIMIT",
                        "The model service is busy. Please retry shortly.",
                        true
                );
            }
            if (modelError.statusCode() == 408 || modelError.statusCode() == 504 || hasTimeoutCause(modelError)) {
                return new ApiErrorDescriptor(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "MODEL_TIMEOUT",
                        "The model response timed out. Please retry.",
                        true
                );
            }
            return new ApiErrorDescriptor(
                    HttpStatus.BAD_GATEWAY,
                    "MODEL_ERROR",
                    "The model service could not complete the request.",
                    true
            );
        }
        if (cause instanceof RejectedExecutionException) {
            return new ApiErrorDescriptor(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVER_BUSY",
                    "The server has reached its streaming capacity. Please retry shortly.",
                    true
            );
        }
        if (cause instanceof DataAccessException) {
            return new ApiErrorDescriptor(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PERSISTENCE_ERROR",
                    "The conversation could not be saved.",
                    true
            );
        }
        if (cause instanceof IllegalArgumentException) {
            return new ApiErrorDescriptor(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    cause.getMessage(),
                    false
            );
        }
        return new ApiErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The request could not be completed.",
                true
        );
    }

    private static Throwable findKnownCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RepeatSubmitException
                    || current instanceof AuthenticationException
                    || current instanceof MaxIterationsException
                    || current instanceof ToolExecutionException
                    || current instanceof ModelException
                    || current instanceof RejectedExecutionException
                    || current instanceof DataAccessException
                    || current instanceof IllegalArgumentException) {
                return current;
            }
            current = current.getCause();
        }
        return error;
    }

    private static boolean hasTimeoutCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}