package io.myclaw.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 把 Spring Bean 的无参方法声明为技能；方法须返回完整技能指令。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyClawSkill {
    String name() default "";
    String description();
}
