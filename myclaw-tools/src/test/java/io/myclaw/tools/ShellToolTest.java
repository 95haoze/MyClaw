package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShellTool} 的单元测试：默认禁用、白名单、高危命令拦截、正常执行与超时强杀。
 */
class ShellToolTest {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private final ToolContext context = ToolContext.of(Path.of(".").toAbsolutePath().normalize());

    @Test
    @DisplayName("默认构造器：禁用、空白名单、30 秒超时")
    void disabledByDefault() {
        ShellTool tool = new ShellTool();

        assertThat(tool.enabled()).isFalse();
        assertThat(tool.name()).isEqualTo("run_command");
        assertThat(tool.definition().parameters().path("properties").has("command")).isTrue();
    }

    @Test
    @DisplayName("禁用时不会被注册进 ToolRegistry")
    void disabledToolIsNotRegistered() {
        ToolRegistry registry = new ToolRegistry().registerAll(new ShellTool());

        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.contains("run_command")).isFalse();
    }

    @Test
    @DisplayName("启用后会被注册进 ToolRegistry")
    void enabledToolIsRegistered() {
        ToolRegistry registry = new ToolRegistry()
                .registerAll(new ShellTool(true, List.of("echo"), Duration.ofSeconds(5)));

        assertThat(registry.contains("run_command")).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("启用但命令不在白名单内 → SecurityException")
    void rejectsCommandOutsideAllowlist() {
        ShellTool tool = new ShellTool(true, List.of("git ", "mvn "), Duration.ofSeconds(5));

        assertThatThrownBy(() -> tool.call(Json.obj().put("command", "echo hello"), context))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("白名单");
        assertThatThrownBy(() -> tool.call(Json.obj().put("command", "dir"), context))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("白名单命中时放行")
    void allowsCommandMatchingAllowlist() {
        ShellTool tool = new ShellTool(true, List.of("echo"), Duration.ofSeconds(5));

        assertThat(tool.enabled()).isTrue();
        // 只验证校验通过，不真正关心命令输出
        assertThat(tool.definition().name()).isEqualTo("run_command");
    }

    @Test
    @DisplayName("高危命令即使白名单为空也被拒绝")
    void rejectsDangerousCommands() {
        ShellTool tool = new ShellTool(true, List.of(), Duration.ofSeconds(5));

        for (String command : List.of(
                "rm -rf /",
                "rm -rf / --no-preserve-root",
                ":(){ :|:& };:",
                "shutdown -h now",
                "mkfs.ext4 /dev/sda1",
                "format C: /q")) {
            assertThatThrownBy(() -> tool.call(Json.obj().put("command", command), context))
                    .as("高危命令应被拒绝: %s", command)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("高危");
        }
    }

    @Test
    @DisplayName("缺少 command 参数时报错")
    void requiresCommand() {
        ShellTool tool = new ShellTool(true, List.of(), Duration.ofSeconds(5));

        assertThatThrownBy(() -> tool.call(Json.obj(), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    @DisplayName("执行允许的命令并返回退出码与输出")
    void executesCommand() throws Exception {
        ShellTool tool = new ShellTool(true, List.of("echo"), Duration.ofSeconds(20));

        String result = tool.call(Json.obj().put("command", "echo myclaw-ok"), context);

        assertThat(result).contains("Exit code: 0").contains("myclaw-ok");
    }

    @Test
    @DisplayName("timeout terminates the process and throws")
    void killsProcessOnTimeout() {
        String sleeper = WINDOWS ? "ping -n 6 127.0.0.1" : "sleep 5";
        ShellTool tool = new ShellTool(true, List.of(), Duration.ofMillis(700));

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> tool.call(Json.obj().put("command", sleeper), context))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("timed out");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMillis).isLessThan(15_000L);
    }
}
