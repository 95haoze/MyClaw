package io.myclaw.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述 {@link MyClawTool} 方法的一个参数，用于生成 JSON Schema。
 *
 * <p>如果不加这个注解，框架会退而使用反射拿到的真实参数名（依赖编译期 {@code -parameters}），
 * 但那样就没有参数说明，模型更容易传错值，因此<b>推荐显式标注</b>。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyClawParam {

    /** 参数名。留空时取编译期保留的真实参数名。 */
    String value() default "";

    /** 参数说明，会写进 JSON Schema 的 description。 */
    String description() default "";

    /** 是否为必填参数。默认必填；选填参数缺失时会传入 {@code null} 或基本类型的零值。 */
    boolean required() default true;
}
