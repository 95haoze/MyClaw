package io.myclaw.core.tool;

import io.myclaw.core.message.ToolCall;
import io.myclaw.core.support.TestTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    @DisplayName("注册后可查找、可导出定义")
    void registersAndExposesDefinitions() {
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());

        assertThat(registry.contains("add")).isTrue();
        assertThat(registry.find("add")).isPresent();
        assertThat(registry.names()).containsExactly("add");
        assertThat(registry.definitions()).singleElement()
                .satisfies(definition -> {
                    assertThat(definition.name()).isEqualTo("add");
                    assertThat(definition.description()).isEqualTo("计算两个整数之和");
                });
    }

    @Test
    @DisplayName("批量注册与注销")
    void registersAllAndUnregisters() {
        ToolRegistry registry = new ToolRegistry()
                .registerAll(TestTools.add(), TestTools.exploding());

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.unregister("add")).isTrue();
        assertThat(registry.unregister("add")).isFalse();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("禁用状态的工具不会被注册")
    void skipsDisabledTools() {
        Tool disabled = new Tool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder("disabled").build();
            }

            @Override
            public String call(JsonNode arguments, ToolContext context) {
                return "should not be called";
            }

            @Override
            public boolean enabled() {
                return false;
            }
        };

        ToolRegistry registry = new ToolRegistry().register(disabled);

        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("同名工具重复注册时，后注册的生效")
    void laterRegistrationWins() {
        ToolRegistry registry = new ToolRegistry()
                .register(named("dup", "第一个"))
                .register(named("dup", "第二个"));

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.execute(ToolCall.of("dup", "{}"), ToolContext.defaults()).content())
                .isEqualTo("第二个");
    }

    @Test
    @DisplayName("调用不存在的工具返回错误结果而不是抛异常")
    void unknownToolYieldsErrorResult() {
        ToolRegistry registry = new ToolRegistry().register(TestTools.add());

        ToolResult result = registry.execute(ToolCall.of("ghost", "{}"), ToolContext.defaults());

        assertThat(result.error()).isTrue();
        assertThat(result.toolName()).isEqualTo("ghost");
        assertThat(result.content()).contains("不存在").contains("add");
        assertThat(result.toModelContent()).startsWith("ERROR:");
    }

    @Test
    @DisplayName("工具抛出的异常被转换成错误结果")
    void toolExceptionBecomesErrorResult() {
        ToolRegistry registry = new ToolRegistry().register(TestTools.exploding());

        ToolResult result = registry.execute(ToolCall.of("explode", "{}"), ToolContext.defaults());

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("内部错误：数据库连接失败");
    }

    @Test
    @DisplayName("HTTP 连接超时转换为中文错误")
    void describesHttpConnectTimeoutInChinese() {
        Tool timeoutTool = new Tool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder("timeout").build();
            }

            @Override
            public String call(JsonNode arguments, ToolContext context) throws Exception {
                throw new java.net.http.HttpConnectTimeoutException("HTTP connect timed out");
            }
        };
        ToolResult result = new ToolRegistry().register(timeoutTool)
                .execute(ToolCall.of("timeout", "{}"), ToolContext.defaults());

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("HTTP 连接超时").contains("代理配置");
    }
    @Test
    @DisplayName("参数不是合法 JSON 时，回填解析错误且不执行工具")
    void malformedArgumentsProduceParseError() {
        TestTools.Recorder recorder = new TestTools.Recorder("rec");
        ToolRegistry registry = new ToolRegistry().register(recorder);

        ToolResult result = registry.execute(ToolCall.of("rec", "{不是合法 JSON"), ToolContext.defaults());

        // 把"你的 JSON 不合法"原样告诉模型，比降级成 {} 再报"缺少参数"更容易让它自我纠正
        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("无法解析 JSON");
        assertThat(recorder.invocations()).isEmpty();
    }

    @Test
    @DisplayName("批量执行保持顺序与一一对应")
    void executesAllInOrder() {
        ToolRegistry registry = new ToolRegistry()
                .register(TestTools.add())
                .register(TestTools.exploding());

        var results = registry.executeAll(
                java.util.List.of(
                        ToolCall.of("add", "{\"a\":1,\"b\":1}"),
                        ToolCall.of("explode", "{}"),
                        ToolCall.of("missing", "{}")),
                ToolContext.defaults());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).content()).isEqualTo("2");
        assertThat(results.get(1).error()).isTrue();
        assertThat(results.get(2).error()).isTrue();
    }

    private static Tool named(String name, String reply) {
        return new Tool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder(name).description("测试").build();
            }

            @Override
            public String call(JsonNode arguments, ToolContext context) {
                return reply;
            }
        };
    }
}
