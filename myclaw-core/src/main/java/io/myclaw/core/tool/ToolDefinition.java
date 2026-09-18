package io.myclaw.core.tool;

import io.myclaw.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * 工具对外的自描述：告诉模型"我叫什么、能干什么、需要什么参数"。
 *
 * @param name        工具名，需全局唯一，建议使用 {@code snake_case}（模型更熟悉）
 * @param description 自然语言描述，是模型决定是否调用的主要依据，务必写清楚
 * @param parameters  JSON Schema 描述的入参对象
 */
public record ToolDefinition(String name, String description, JsonNode parameters) {

    public ToolDefinition {
        Objects.requireNonNull(name, "工具名不能为空");
        if (name.isBlank()) {
            throw new IllegalArgumentException("工具名不能为空白");
        }
        description = description == null ? "" : description;
        parameters = parameters == null ? JsonSchema.object().build() : parameters;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String description = "";
        private JsonNode parameters;

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(JsonNode parameters) {
            this.parameters = parameters;
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(name, description, parameters);
        }
    }

    @Override
    public String toString() {
        return name + "(" + Json.write(parameters) + ")";
    }
}
