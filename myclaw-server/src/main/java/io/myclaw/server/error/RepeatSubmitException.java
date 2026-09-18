package io.myclaw.server.error;

public final class RepeatSubmitException extends RuntimeException {
    public RepeatSubmitException(String message) {
        super(message);
    }
}