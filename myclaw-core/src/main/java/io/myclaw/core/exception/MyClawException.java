package io.myclaw.core.exception;

/**
 * MyClaw 所有异常的基类（非受检）。
 */
public class MyClawException extends RuntimeException {

    public MyClawException(String message) {
        super(message);
    }

    public MyClawException(String message, Throwable cause) {
        super(message, cause);
    }
}
