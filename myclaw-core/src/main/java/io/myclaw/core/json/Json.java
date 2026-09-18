package io.myclaw.core.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * MyClaw 内部统一的 JSON 门面。
 *
 * <p>内核只依赖 Jackson 3（Spring Boot 4 的默认 JSON 实现），所有对 JSON 的处理都收敛到这里，
 * 这样将来更换 JSON 库时只需要改这一个类。所有异常都被转换为 {@link IllegalArgumentException}，
 * 避免调用方处理受检异常。
 */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    /** 返回共享的、线程安全的 {@link ObjectMapper}。 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 新建一个空的 JSON 对象节点。 */
    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    /** 新建一个空的 JSON 数组节点。 */
    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    /** 解析 JSON 字符串；解析失败抛出 {@link IllegalArgumentException}。 */
    public static JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return obj();
        }
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 JSON: " + abbreviate(text) + " -> " + e.getMessage(), e);
        }
    }

    /** 解析 JSON 字符串，失败时返回 {@code fallback} 而不是抛异常。 */
    public static JsonNode parseOr(String text, JsonNode fallback) {
        try {
            return parse(text);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** 序列化为紧凑 JSON。 */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法序列化对象: " + e.getMessage(), e);
        }
    }

    /** 序列化为带缩进的 JSON，便于日志与调试。 */
    public static String pretty(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法序列化对象: " + e.getMessage(), e);
        }
    }

    /**
     * 把任意 POJO / Map / List 转换成 JSON 节点树。
     *
     * <p>用于把用户通过 {@code ChatOptions.extra(...)} 传入的厂商私有参数透传给 provider。
     */
    public static JsonNode toNode(Object value) {
        if (value instanceof JsonNode node) {
            return node;
        }
        try {
            return MAPPER.convertValue(value, JsonNode.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "无法把 " + (value == null ? "null" : value.getClass().getSimpleName()) + " 转换为 JSON 节点: " + e.getMessage(), e);
        }
    }

    /** 读取节点的文本值，节点缺失或为 null 时返回 {@code null}。 */
    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asString();
    }

    /** 读取节点的文本值，缺失时返回默认值。 */
    public static String text(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return value == null ? defaultValue : value;
    }

    private static String abbreviate(String text) {
        String flat = text.replace('\n', ' ');
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "...";
    }
}
