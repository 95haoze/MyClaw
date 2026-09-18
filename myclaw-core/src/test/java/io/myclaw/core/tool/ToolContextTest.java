package io.myclaw.core.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolContextTest {

    private final Path workDir = Path.of("target", "sandbox").toAbsolutePath().normalize();

    @Test
    @DisplayName("相对路径解析到工作目录之下")
    void resolvesRelativePaths() {
        ToolContext context = ToolContext.of(workDir);

        assertThat(context.resolve("a/b.txt")).isEqualTo(workDir.resolve("a/b.txt"));
        assertThat(context.resolve("./a/../b.txt")).isEqualTo(workDir.resolve("b.txt"));
    }

    @Test
    @DisplayName("工作目录内的绝对路径被允许")
    void allowsAbsolutePathInsideSandbox() {
        ToolContext context = ToolContext.of(workDir);

        assertThat(context.resolve(workDir.resolve("x.txt").toString())).isEqualTo(workDir.resolve("x.txt"));
    }

    @Test
    @DisplayName("试图越出工作目录的路径被拒绝")
    void rejectsPathTraversal() {
        ToolContext context = ToolContext.of(workDir);

        assertThatThrownBy(() -> context.resolve("../secret.txt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("工作目录之外");
    }

    @Test
    @DisplayName("工作目录之外的绝对路径被拒绝")
    void rejectsAbsolutePathOutsideSandbox() {
        ToolContext context = ToolContext.of(workDir);
        Path outside = workDir.getParent().getParent().resolve("outside.txt");

        assertThatThrownBy(() -> context.resolve(outside.toString()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("require 缺失或类型不符时给出明确错误")
    void requireValidatesPresenceAndType() {
        ToolContext context = ToolContext.builder()
                .workingDirectory(workDir)
                .attribute("sessionId", "s-1")
                .attribute("count", 3)
                .build();

        assertThat(context.require("sessionId", String.class)).isEqualTo("s-1");
        assertThat(context.attribute("count")).contains(3);
        assertThat(context.attribute("missing")).isEmpty();

        assertThatThrownBy(() -> context.require("missing", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少必需属性");
        assertThatThrownBy(() -> context.require("count", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("类型应为");
    }

    @Test
    @DisplayName("withAttribute 派生新上下文且不污染原上下文")
    void withAttributeReturnsDerivedCopy() {
        ToolContext base = ToolContext.of(workDir);

        ToolContext derived = base.withAttribute("k", "v");

        assertThat(base.attribute("k")).isEmpty();
        assertThat(derived.attribute("k")).contains("v");
        assertThat(derived.workingDirectory()).isEqualTo(base.workingDirectory());
    }

    @Test
    @DisplayName("JSON Schema 正确表达必填与可选参数")
    void buildsJsonSchema() {
        ObjectNode schema = JsonSchema.object()
                .string("city", "城市名称")
                .integer("days", "预报天数", false)
                .enumOf("unit", "温度单位", List.of("celsius", "fahrenheit"), false)
                .arrayOfStrings("tags", "标签", false)
                .build();

        JsonNode parsed = io.myclaw.core.json.Json.parse(io.myclaw.core.json.Json.write(schema));

        assertThat(parsed.path("type").asString()).isEqualTo("object");
        assertThat(parsed.path("properties").path("city").path("type").asString()).isEqualTo("string");
        assertThat(parsed.path("properties").path("city").path("description").asString()).isEqualTo("城市名称");
        assertThat(parsed.path("properties").path("days").path("type").asString()).isEqualTo("integer");
        assertThat(parsed.path("properties").path("unit").path("enum")).hasSize(2);
        assertThat(parsed.path("properties").path("tags").path("type").asString()).isEqualTo("array");
        assertThat(parsed.path("properties").path("tags").path("items").path("type").asString()).isEqualTo("string");
        assertThat(parsed.path("required")).hasSize(1);
        assertThat(parsed.path("required").get(0).asString()).isEqualTo("city");
    }

    @Test
    @DisplayName("空 Schema 也是合法的 object")
    void emptySchemaIsValid() {
        JsonNode schema = JsonSchema.object().build();

        assertThat(schema.path("type").asString()).isEqualTo("object");
        assertThat(schema.has("required")).isFalse();
    }
}
