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

class CodingToolsTest {
    private Path tempDir;

    @BeforeEach
    void createWorkspace() throws Exception {
        tempDir = Path.of("target", "coding-tools-test", UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
        Files.createDirectories(tempDir);
    }

    @Test
    void searchesTextAndSkipsBuildDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(tempDir.resolve("src/App.java"), "class App {\n  void chat() {}\n}\n");
        Files.writeString(tempDir.resolve("target/Generated.java"), "void chat() {}");
        String result = new SearchTextTool().call(
                Json.obj().put("query", "chat").put("glob", "*.java"), ToolContext.of(tempDir));
        assertThat(result).contains("src").contains("App.java:2").doesNotContain("Generated.java");
    }

    @Test
    void replacesOnlyWhenExpectedOccurrenceCountMatches() throws Exception {
        Path file = tempDir.resolve("App.java");
        Files.writeString(file, "old\nold\n");
        ReplaceTextTool tool = new ReplaceTextTool();
        ToolContext context = ToolContext.of(tempDir);
        assertThatThrownBy(() -> tool.call(
                Json.obj().put("path", "App.java").put("oldText", "old").put("newText", "new"), context))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("found 2");
        assertThat(Files.readString(file)).isEqualTo("old\nold\n");
        tool.call(Json.obj().put("path", "App.java").put("oldText", "old")
                .put("newText", "new").put("expectedOccurrences", 2), context);
        assertThat(Files.readString(file)).isEqualTo("new\nnew\n");
    }

    @Test
    void replacementCannotEscapeWorkingDirectory() {
        assertThatThrownBy(() -> new ReplaceTextTool().call(
                Json.obj().put("path", "../outside.txt").put("oldText", "a").put("newText", "b"),
                ToolContext.of(tempDir))).isInstanceOf(SecurityException.class);
    }
}
