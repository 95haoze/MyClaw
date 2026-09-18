package io.myclaw.core.support;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用工具集。
 */
public final class TestTools {

    private TestTools() {
    }

    /** 两数相加，用于验证"工具调用 -> 结果回填 -> 最终答案"的完整链路。 */
    public static Tool add() {
        return new Tool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder("add")
                        .description("计算两个整数之和")
                        .parameters(JsonSchema.object()
                                .integer("a", "第一个加数")
                                .integer("b", "第二个加数")
                                .build())
                        .build();
            }

            @Override
            public String call(JsonNode arguments, ToolContext context) {
                return String.valueOf(arguments.path("a").asInt() + arguments.path("b").asInt());
            }
        };
    }

    /** 永远抛异常的工具，用于验证容错。 */
    public static Tool exploding() {
        return new Tool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder("explode")
                        .description("总是抛出异常")
                        .parameters(JsonSchema.object().build())
                        .build();
            }

            @Override
            public String call(JsonNode arguments, ToolContext context) {
                throw new IllegalStateException("内部错误：数据库连接失败");
            }
        };
    }

    /** 记录每次调用实参的工具，用于断言上下文传递是否正确。 */
    public static final class Recorder implements Tool {
        private final String toolName;
        private final List<JsonNode> invocations = new ArrayList<>();
        private final List<ToolContext> contexts = new ArrayList<>();

        public Recorder(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.builder(toolName)
                    .description("记录调用参数")
                    .parameters(JsonSchema.object().string("payload", "任意内容", false).build())
                    .build();
        }

        @Override
        public String call(JsonNode arguments, ToolContext context) {
            invocations.add(arguments);
            contexts.add(context);
            return "recorded";
        }

        public List<JsonNode> invocations() {
            return List.copyOf(invocations);
        }

        public List<ToolContext> contexts() {
            return List.copyOf(contexts);
        }
    }
}
