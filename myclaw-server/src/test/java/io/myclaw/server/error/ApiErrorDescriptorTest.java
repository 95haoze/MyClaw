package io.myclaw.server.error;

import io.myclaw.core.exception.MaxIterationsException;
import io.myclaw.core.exception.EmptyModelResponseException;
import io.myclaw.core.exception.ModelException;
import io.myclaw.core.exception.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorDescriptorTest {

    @Test
    void mapsInvalidCredentials() {
        ApiErrorDescriptor descriptor = ApiErrorDescriptor.from(new BadCredentialsException("Bad credentials"));

        assertDescriptor(new BadCredentialsException("Bad credentials"), HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_CREDENTIALS", false);
        assertThat(descriptor.message()).isEqualTo("用户名或密码错误");
    }

    @Test
    void mapsIterationLimit() {
        assertDescriptor(new MaxIterationsException(8), HttpStatus.UNPROCESSABLE_CONTENT,
                "AGENT_MAX_ITERATIONS", true);
    }

    @Test
    void mapsToolFailure() {
        assertDescriptor(new ToolExecutionException("command failed"), HttpStatus.UNPROCESSABLE_CONTENT,
                "TOOL_EXECUTION_FAILED", true);
    }

    @Test
    void mapsModelAuthenticationFailure() {
        ApiErrorDescriptor descriptor = ApiErrorDescriptor.from(new ModelException("unauthorized", 401, null));

        assertThat(descriptor.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(descriptor.code()).isEqualTo("MODEL_AUTH_FAILED");
        assertThat(descriptor.message()).contains("API Key");
        assertThat(descriptor.retryable()).isFalse();
    }
    @Test
    void mapsModelRateLimit() {
        assertDescriptor(new ModelException("limited", 429, null), HttpStatus.TOO_MANY_REQUESTS,
                "MODEL_RATE_LIMIT", true);
    }

    @Test
    void mapsWrappedModelTimeout() {
        RuntimeException error = new RuntimeException(
                new ModelException("timeout", -1, new HttpTimeoutException("timed out")));

        assertDescriptor(error, HttpStatus.GATEWAY_TIMEOUT, "MODEL_TIMEOUT", true);
    }

    @Test
    void mapsEmptyModelResponse() {
        ApiErrorDescriptor descriptor = ApiErrorDescriptor.from(new EmptyModelResponseException());

        assertThat(descriptor.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(descriptor.code()).isEqualTo("MODEL_EMPTY_RESPONSE");
        assertThat(descriptor.message()).contains("模型未返回有效内容");
        assertThat(descriptor.retryable()).isTrue();
    }
    @Test
    void mapsOtherModelFailures() {
        assertDescriptor(new ModelException("upstream", 500, null), HttpStatus.BAD_GATEWAY,
                "MODEL_ERROR", true);
    }

    @Test
    void mapsRejectedStreamingTask() {
        assertDescriptor(new RejectedExecutionException("full"), HttpStatus.SERVICE_UNAVAILABLE,
                "SERVER_BUSY", true);
    }

    @Test
    void mapsWrappedPersistenceFailure() {
        RuntimeException error = new RuntimeException(
                new DataAccessResourceFailureException("database unavailable"));

        assertDescriptor(error, HttpStatus.INTERNAL_SERVER_ERROR, "PERSISTENCE_ERROR", true);
    }

    @Test
    void mapsInvalidRequestAsNonRetryable() {
        assertDescriptor(new IllegalArgumentException("bad request"), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", false);
    }

    @Test
    void hidesUnexpectedInternalErrorDetails() {
        ApiErrorDescriptor descriptor = ApiErrorDescriptor.from(
                new IllegalStateException("secret implementation detail"));

        assertThat(descriptor.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(descriptor.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(descriptor.message()).doesNotContain("secret");
        assertThat(descriptor.retryable()).isTrue();
    }

    private static void assertDescriptor(
            Throwable error,
            HttpStatus status,
            String code,
            boolean retryable
    ) {
        ApiErrorDescriptor descriptor = ApiErrorDescriptor.from(error);

        assertThat(descriptor.status()).isEqualTo(status);
        assertThat(descriptor.code()).isEqualTo(code);
        assertThat(descriptor.retryable()).isEqualTo(retryable);
        assertThat(descriptor.body().code()).isEqualTo(code);
    }
}