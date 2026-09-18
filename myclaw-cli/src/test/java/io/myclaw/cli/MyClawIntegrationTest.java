package io.myclaw.cli;

import io.myclaw.core.agent.Agent;
import io.myclaw.core.agent.AgentResponse;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolRegistry;
import io.myclaw.core.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：真实启动 Spring 容器，验证"自动配置 + 注解工具 + Agent 循环"整条链路。
 *
 * <p>使用 {@code offline} profile，因此不需要 API Key、不联网，完全确定性。
 */
@SpringBootTest(properties = "myclaw.cli.enabled=false")
@ActiveProfiles("offline")
class MyClawIntegrationTest {

    @Autowired
    private Agent agent;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    @DisplayName("容器启动后 Agent 与工具注册表都已就绪")
    void contextProvidesAgentAndTools() {
        assertThat(agent).isNotNull();
        assertThat(agent.model().name()).isEqualTo("offline-demo");
        assertThat(agent.maxIterations()).isEqualTo(8);
    }

    @Test
    @DisplayName("内置工具严格按 application.yml 的开关注册（示例里写文件与执行命令都是打开的）")
    void registersBuiltinToolsRespectingConfig() {
        assertThat(toolRegistry.names())
                .contains("calculate", "current_time", "read_file", "list_files", "http_fetch",
                        "write_file", "run_command");
    }

    @Test
    @DisplayName("@MyClawTool 注解方法被自动扫描并注册")
    void registersAnnotatedTools() {
        assertThat(toolRegistry.names())
                .contains("random_number", "system_info", "convert_temperature", "jvm_uptime");
    }

    @Test
    @DisplayName("枚举参数被翻译成 JSON Schema 的 enum 约束")
    void enumParameterBecomesSchemaEnum() {
        var definition = toolRegistry.find("convert_temperature").orElseThrow().definition();

        assertThat(definition.description()).contains("换算温度");
        assertThat(definition.parameters().path("properties").path("from").path("enum")).hasSize(3);
        assertThat(definition.parameters().path("required")).hasSize(3);
    }

    @Test
    @DisplayName("整条 ReAct 链路跑通：模型请求工具 -> 执行 -> 结果回填 -> 最终答案")
    void completesFullAgentLoop() {
        AgentResponse response = agent.run("帮我算一下 1234 * 5678");

        assertThat(response.usedTools()).isTrue();
        assertThat(response.toolResults()).hasSize(1);
        assertThat(response.toolResults().getFirst().content()).isEqualTo("7006652");
        assertThat(response.content()).contains("7006652");
        assertThat(response.iterations()).isEqualTo(2);
    }

    @Test
    @DisplayName("注解工具能被注册表按名字直接调用")
    void invokesAnnotatedToolDirectly() {
        ToolResult result = toolRegistry.execute(
                ToolCall.of("random_number", "{\"min\":1,\"max\":6}"), ToolContext.defaults());

        assertThat(result.error()).isFalse();
        assertThat(Integer.parseInt(result.content())).isBetween(1, 6);
    }

    @Test
    @DisplayName("缺少必填参数时给出明确的错误信息")
    void reportsMissingRequiredParameter() {
        ToolResult result = toolRegistry.execute(
                ToolCall.of("convert_temperature", "{\"value\":100,\"to\":\"FAHRENHEIT\"}"),
                ToolContext.defaults());

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("缺少必填参数").contains("from");
    }

    @Test
    @DisplayName("非法枚举值会列出所有可选值，便于模型自我纠正")
    void reportsInvalidEnumValue() {
        ToolResult result = toolRegistry.execute(
                ToolCall.of("convert_temperature",
                        "{\"value\":100,\"from\":\"KELVINX\",\"to\":\"FAHRENHEIT\"}"),
                ToolContext.defaults());

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("非法").contains("CELSIUS").contains("FAHRENHEIT");
    }

    @Test
    @DisplayName("工具内部抛出的业务异常被安全地回填给模型")
    void surfacesBusinessExceptionAsToolError() {
        ToolResult result = toolRegistry.execute(
                ToolCall.of("random_number", "{\"min\":10,\"max\":1}"), ToolContext.defaults());

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("min 不能大于 max");
    }
}
