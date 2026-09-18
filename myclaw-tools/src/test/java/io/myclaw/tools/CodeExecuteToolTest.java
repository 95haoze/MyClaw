package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeExecuteToolTest {
    private Path workspace;
    private CodeExecuteTool tool;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Path.of("target", "code-execute-test", UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
        Files.createDirectories(workspace);
        tool = new CodeExecuteTool(true);
    }

    @Test
    void executesPythonWithJsonArgumentsAndReportsGeneratedFiles() throws Exception {
        String code = """
                import pathlib, sys
                print("hello " + sys.argv[1])
                pathlib.Path("answer.txt").write_text(sys.argv[2], encoding="utf-8")
                """;

        String result = tool.call(
                Json.obj()
                        .put("language", "python")
                        .put("code", code)
                        .put("args", "[\"MyClaw\",\"42\"]"),
                ToolContext.of(workspace)
        );

        assertThat(result).contains("\"exitCode\":0")
                .contains("hello MyClaw")
                .contains("answer.txt");
        assertThat(Files.readString(workspace.resolve("answer.txt"))).isEqualTo("42");
    }

    @Test
    void nonZeroExitIsAToolFailureWithCapturedStderr() {
        assertThatThrownBy(() -> tool.call(
                Json.obj().put("language", "python")
                        .put("code", "import sys\nprint('broken', file=sys.stderr)\nsys.exit(7)"),
                ToolContext.of(workspace)
        )).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exit code 7")
                .hasMessageContaining("broken");
    }

    @Test
    void timeoutTerminatesTheChildProcess() {
        assertThatThrownBy(() -> tool.call(
                Json.obj().put("language", "python")
                        .put("code", "import time\ntime.sleep(10)")
                        .put("timeoutSeconds", 1),
                ToolContext.of(workspace)
        )).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void validatesLanguageArgumentsAndSkillDirectory() {
        ToolContext context = ToolContext.of(workspace);
        assertThatThrownBy(() -> tool.call(
                Json.obj().put("language", "java").put("code", "x"), context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language");
        assertThatThrownBy(() -> tool.call(
                Json.obj().put("language", "python").put("code", "print(1)")
                        .put("args", "[1]"), context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("string");
        assertThatThrownBy(() -> tool.call(
                Json.obj().put("language", "python").put("code", "print(1)")
                        .put("skillName", "../escape"), context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("skillName");
    }

    @Test
    void disabledByDefault() {
        assertThat(new CodeExecuteTool().enabled()).isFalse();
        assertThat(new CodeExecuteTool(true).enabled()).isTrue();
    }
}