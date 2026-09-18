package io.myclaw.tools;

import io.myclaw.core.exception.ToolExecutionException;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令执行工具（<b>默认禁用</b>）。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code command}（string，必填）：要执行的命令</li>
 * </ul>
 *
 * <p>返回：命令的退出码与合并后的 stdout/stderr（超过 20000 字符截断）；超时会强杀进程并返回超时错误。
 *
 * <p>安全模型（三重）：
 * <ol>
 *   <li>{@link #enabled()} 默认返回 false —— {@code ToolRegistry} 会直接跳过注册，模型根本看不到它</li>
 *   <li>可选的命令前缀白名单：非空时命令必须以其中之一开头，否则抛 {@link SecurityException}</li>
 *   <li>无论是否配置白名单，都拒绝包含删除根目录、fork 炸弹、格式化磁盘、关机、mkfs
 *       等高危片段的命令（见 {@link #DANGEROUS_FRAGMENTS}，比较时忽略大小写）</li>
 * </ol>
 *
 * <p>工作目录固定为 {@link ToolContext#workingDirectory()}；Windows 使用 {@code cmd.exe /d /c}
 * （{@code /d} 关闭注册表 AutoRun 钩子），其它平台使用 {@code /bin/sh -c}。
 */
public class ShellTool implements Tool {

    /** 默认超时时间。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final int MAX_OUTPUT_CHARS = 20000;

    /** 任何情况下都拒绝执行的高危片段（统一按小写比较）。 */
    private static final List<String> DANGEROUS_FRAGMENTS = List.of(
            "rm -rf /", "rm -fr /", "rm -rf /*", ":(){", "format ", "mkfs", "shutdown", "reboot",
            "del /f /s /q c:\\", "del /q /f /s c:\\", "rd /s /q c:\\", "> /dev/sda", "dd if=",
            "chmod -r 777 /", "halt");

    private final boolean enabled;
    private final List<String> allowlistPrefixes;
    private final Duration timeout;

    /** 默认构造器：禁用 + 空白名单 + 30 秒超时。 */
    public ShellTool() {
        this(false, List.of(), DEFAULT_TIMEOUT);
    }

    /**
     * @param enabled           是否启用；false 时不会被 {@link io.myclaw.core.tool.ToolRegistry} 注册
     * @param allowlistPrefixes 允许的命令前缀（空表示不限制前缀，但仍会做高危片段检查）
     * @param timeout           超时时间，null 或非正数时回退为 {@link #DEFAULT_TIMEOUT}
     */
    public ShellTool(boolean enabled, List<String> allowlistPrefixes, Duration timeout) {
        this.enabled = enabled;
        this.allowlistPrefixes = allowlistPrefixes == null ? List.of() : allowlistPrefixes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .toList();
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("run_command")
                .description("""
                        在工作目录中执行一条系统命令，返回退出码与合并后的标准输出/错误输出。
                        受安全策略约束：只有白名单前缀允许的命令才能执行，高危命令一律被拒绝。""")
                .parameters(JsonSchema.object()
                        .string("command", "要执行的命令，例如 mvn -B test")
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws IOException {
        String command = ToolSupport.requiredString(arguments, "command").trim();
        checkSafety(command);

        ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
        builder.directory(context.workingDirectory().toFile());
        builder.redirectErrorStream(true);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("无法启动命令 `" + command + "`: " + e.getMessage(), e);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().name("myclaw-shell-reader").start(() -> {
            try (InputStream input = process.getInputStream()) {
                input.transferTo(buffer);
            } catch (IOException ignored) {
                // 进程被强杀时读取线程可能异常退出，忽略即可
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("命令执行被中断: " + command, e);
        }

        if (!finished) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            joinQuietly(reader);
            String partial = ToolSupport.truncate(
                    buffer.toString(StandardCharsets.UTF_8), MAX_OUTPUT_CHARS
            );
            throw new IOException(
                    "Command timed out after " + timeout.toSeconds() + " seconds: " + command
                            + "\nCaptured output:\n" + (partial.isBlank() ? "(no output)" : partial)
            );
        }
        joinQuietly(reader);

        int exitCode = process.exitValue();
        String output = ToolSupport.truncate(buffer.toString(StandardCharsets.UTF_8), MAX_OUTPUT_CHARS).strip();

        if (exitCode != 0) {
            throw new IOException(
                    "Command failed with exit code " + exitCode + ": " + command
                            + "\nOutput:\n" + (output.isEmpty() ? "(no output)" : output)
            );
        }
        return "Command: " + command + "\nExit code: 0\nOutput:\n"
                + (output.isEmpty() ? "(no output)" : output);
    }

    /** 组装平台对应的 shell 调用形式。 */
    private static List<String> shellCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // /d 关闭 cmd.exe 的注册表 AutoRun 钩子：既避免被注入额外的启动命令，
            // 也避免 AutoRun 出错时污染退出码与输出
            return List.of("cmd.exe", "/d", "/c", command);
        }
        return List.of("/bin/sh", "-c", command);
    }

    /** 高危片段检查 + 白名单前缀检查。 */
    private void checkSafety(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        for (String fragment : DANGEROUS_FRAGMENTS) {
            if (lower.contains(fragment)) {
                throw new SecurityException("拒绝执行包含高危片段的命令: `" + fragment + "`，原始命令: " + command);
            }
        }
        if (allowlistPrefixes.isEmpty()) {
            return;
        }
        boolean allowed = allowlistPrefixes.stream()
                .anyMatch(prefix -> lower.startsWith(prefix.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new SecurityException("命令不在白名单内，仅允许以 " + allowlistPrefixes + " 开头。实际命令: " + command);
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
