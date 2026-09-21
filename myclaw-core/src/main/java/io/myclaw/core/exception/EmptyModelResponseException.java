package io.myclaw.core.exception;

/** The model completed successfully but returned neither text nor tool calls. */
public final class EmptyModelResponseException extends ModelException {

    public EmptyModelResponseException() {
        super("模型未返回任何有效内容");
    }
}