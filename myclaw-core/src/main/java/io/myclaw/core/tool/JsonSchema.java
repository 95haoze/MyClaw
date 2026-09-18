package io.myclaw.core.tool;

import io.myclaw.core.json.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * JSON Schema 构造器 —— 用来描述工具的入参，交给模型理解"这个工具怎么调用"。
 *
 * <p>Schema 的质量直接决定模型调用工具的正确率，所以框架把它做成一等公民。
 * 使用方式：
 * <pre>{@code
 * ObjectNode schema = JsonSchema.object()
 *         .string("city", "城市名称，例如 杭州")
 *         .integer("days", "预报天数，1-7", false)
 *         .enumOf("unit", "温度单位", List.of("celsius", "fahrenheit"), false)
 *         .build();
 * }</pre>
 *
 * <p>注意：{@code x(name, description)} 形式的参数默认是必填的；需要可选参数时使用
 * 带 {@code boolean required} 的重载。
 */
public final class JsonSchema {

    private JsonSchema() {
    }

    /** 开始构造一个 {@code type=object} 的 Schema。 */
    public static Builder object() {
        return new Builder();
    }

    /** 独立的字符串 Schema，用于数组元素等场景。 */
    public static ObjectNode stringSchema() {
        return typed("string");
    }

    /** 独立的整数 Schema。 */
    public static ObjectNode integerSchema() {
        return typed("integer");
    }

    /** 独立的数字 Schema。 */
    public static ObjectNode numberSchema() {
        return typed("number");
    }

    /** 独立的布尔 Schema。 */
    public static ObjectNode booleanSchema() {
        return typed("boolean");
    }

    private static ObjectNode typed(String type) {
        ObjectNode node = Json.obj();
        node.put("type", type);
        return node;
    }

    /** 对象型 Schema 的流式构造器。 */
    public static final class Builder {

        private final ObjectNode root = Json.obj();
        private final ObjectNode properties = Json.obj();
        private final ArrayNode required = Json.arr();

        private Builder() {
        }

        /** 添加一个必填字符串参数。 */
        public Builder string(String name, String description) {
            return string(name, description, true);
        }

        public Builder string(String name, String description, boolean isRequired) {
            return add(name, stringSchema(), description, isRequired);
        }

        /** 添加一个必填整数参数。 */
        public Builder integer(String name, String description) {
            return integer(name, description, true);
        }

        public Builder integer(String name, String description, boolean isRequired) {
            return add(name, integerSchema(), description, isRequired);
        }

        /** 添加一个必填数字参数。 */
        public Builder number(String name, String description) {
            return number(name, description, true);
        }

        public Builder number(String name, String description, boolean isRequired) {
            return add(name, numberSchema(), description, isRequired);
        }

        /** 添加一个必填布尔参数。 */
        public Builder bool(String name, String description) {
            return bool(name, description, true);
        }

        public Builder bool(String name, String description, boolean isRequired) {
            return add(name, booleanSchema(), description, isRequired);
        }

        /** 添加一个必填的枚举字符串参数。 */
        public Builder enumOf(String name, String description, List<String> values) {
            return enumOf(name, description, values, true);
        }

        public Builder enumOf(String name, String description, List<String> values, boolean isRequired) {
            ObjectNode schema = stringSchema();
            ArrayNode options = Json.arr();
            values.forEach(options::add);
            schema.set("enum", options);
            return add(name, schema, description, isRequired);
        }

        /** 添加一个必填的字符串数组参数。 */
        public Builder arrayOfStrings(String name, String description) {
            return arrayOfStrings(name, description, true);
        }

        public Builder arrayOfStrings(String name, String description, boolean isRequired) {
            return arrayOf(name, description, stringSchema(), isRequired);
        }

        /** 添加一个数组参数，元素结构由 {@code items} 指定。 */
        public Builder arrayOf(String name, String description, JsonNode items, boolean isRequired) {
            ObjectNode schema = typed("array");
            schema.set("items", items);
            return add(name, schema, description, isRequired);
        }

        /** 直接塞入一个自定义 Schema。 */
        public Builder raw(String name, JsonNode schema, boolean isRequired) {
            return add(name, schema, null, isRequired);
        }

        private Builder add(String name, JsonNode schema, String description, boolean isRequired) {
            if (description != null && schema instanceof ObjectNode objectSchema) {
                objectSchema.put("description", description);
            }
            properties.set(name, schema);
            if (isRequired) {
                required.add(name);
            }
            return this;
        }

        public ObjectNode build() {
            root.put("type", "object");
            root.set("properties", properties);
            if (!required.isEmpty()) {
                root.set("required", required);
            }
            return root;
        }
    }
}
