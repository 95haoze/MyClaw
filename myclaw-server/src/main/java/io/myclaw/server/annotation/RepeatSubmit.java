package io.myclaw.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Prevents the same authenticated user from submitting the same request during a short time window. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {
    /** Debounce window in milliseconds. */
    long interval() default 500;

    /** Message returned when a duplicate submission is blocked. */
    String message() default "操作过于频繁，请稍后再试";

    /** Include a stable request-parameter fingerprint in the lock key. */
    boolean checkParams() default true;
}