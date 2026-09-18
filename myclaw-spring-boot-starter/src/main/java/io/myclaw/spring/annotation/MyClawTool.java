package io.myclaw.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把一个 Spring Bean 的方法声明为 Agent 可调用的工具。
 *
 * <p>被标注的方法必须满足：
 * <ul>
 *   <li>{@code public}</li>
 *   <li>参数类型限于 {@code String / 基本类型及其包装类 / BigDecimal / enum / Path}</li>
 *   <li>返回值会转成字符串回填给模型（{@code String} 直接用，其它类型转 JSON）</li>
 * </ul>
 *
 * <p>这些方法在 Spring 上下文启动完成后会被自动扫描并注册进
 * {@link io.myclaw.core.tool.ToolRegistry}，无需任何额外配置。
 *
 * <pre>{@code
 * @Component
 * public class WeatherTools {
 *
 *     @MyClawTool(name = "get_weather", description = "查询指定城市的实时天气。当用户询问天气时使用。")
 *     public String getWeather(
 *             @MyClawParam(value = "city", description = "城市名称，例如 杭州") String city,
 *             @MyClawParam(value = "unit", description = "温度单位", required = false) String unit) {
 *         return "杭州 晴 26°C";
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyClawTool {

    /**
     * 工具名，需全局唯一。留空时使用方法名。
     *
     * <p>建议显式指定为 {@code snake_case} 英文名（例如 {@code get_weather}），模型对这类命名最熟悉。
     */
    String name() default "";

    /**
     * 工具用途的自然语言描述 —— <b>这是模型决定是否调用该工具的最主要依据</b>，务必写清楚
     * "什么时候该用它"。留空时框架会退化成方法名并给出警告。
     */
    String description() default "";
}
