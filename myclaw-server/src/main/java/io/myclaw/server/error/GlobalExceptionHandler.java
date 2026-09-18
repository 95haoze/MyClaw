package io.myclaw.server.error;

import io.myclaw.server.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(RuntimeException exception) {
        ApiErrorDescriptor descriptor = ApiErrorDescriptor.from(exception);
        if (descriptor.status().is5xxServerError()) {
            log.error("请求处理失败，错误码={}", descriptor.code(), exception);
        } else {
            log.warn("请求处理失败，错误码={}，原因={}", descriptor.code(), exception.getMessage());
        }
        return ResponseEntity.status(descriptor.status())
                .body(ApiResponse.error(descriptor.code(), descriptor.message(), descriptor.retryable()));
    }
}