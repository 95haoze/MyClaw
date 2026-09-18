package io.myclaw.core.tool;

import tools.jackson.databind.JsonNode;

/**
 * 一个可被 Agent 调用的工具。
 *
 * <p>实现类应当是无状态的、线程安全的 —— 所有与"本次调用"相关的信息都通过
 * {@link ToolContext} 传入，这样同一个工具实例可以被多个 Agent、多个会话并发复用。
 *
 * <p>最小实现：
 * <pre>{@code
 * public class WeatherTool implements Tool {
 *     public ToolDefinition definition() {
 *         return ToolDefinition.builder("get_weather")
 *                 .description("查询指定城市的实时天气")
 *                 .parameters(JsonSchema.object().string("city", "城市名").build())
 *                 .build();
 *     }
 *     public String call(JsonNode args, ToolContext ctx) {
 *         return "杭州 晴 26°C";
 *     }
 * }
 * }</pre>
 */
public interface Tool {

    /** 工具的自描述信息。每次调用都应当返回等价内容。 */
    ToolDefinition definition();

    /**
     * 执行工具。
     *
     * @param arguments 模型生成的参数，已解析为 JSON 对象（可能缺少或含有多余字段，需要自行校验）
     * @param context   本次调用的上下文（工作目录、Agent 名称、自定义属性等）
     * @return 返回给模型的文本结果。抛出的异常会被 {@link ToolRegistry} 捕获并转成错误结果回填给模型，
     *         让模型有机会自行纠错，而不是让整个 Agent 崩掉。
     */
    String call(JsonNode arguments, ToolContext context) throws Exception;

    /** 工具名，默认取自 {@link #definition()}。 */
    default String name() {
        return definition().name();
    }

    /** 是否启用。返回 false 的工具不会被注册进 {@link ToolRegistry}。 */
    default boolean enabled() {
        return true;
    }
}
