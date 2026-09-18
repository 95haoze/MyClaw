package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ArrayNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileEditingToolsTest {
    @TempDir Path workspace;

    @Test
    void managesDirectoriesCopyMoveAndRecursiveDelete() throws Exception {
        ToolContext context = ToolContext.of(workspace);
        FileManageTool tool = new FileManageTool();
        tool.call(Json.obj().put("operation", "mkdir").put("source", "src/nested"), context);
        Files.writeString(workspace.resolve("src/nested/a.txt"), "hello");

        tool.call(Json.obj().put("operation", "copy").put("source", "src")
                .put("destination", "copy").put("recursive", true), context);
        tool.call(Json.obj().put("operation", "move").put("source", "copy/nested/a.txt")
                .put("destination", "copy/renamed.txt"), context);

        assertThat(Files.readString(workspace.resolve("copy/renamed.txt"))).isEqualTo("hello");
        assertThatThrownBy(() -> tool.call(Json.obj().put("operation", "delete").put("source", "copy"), context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recursive=true");
        tool.call(Json.obj().put("operation", "delete").put("source", "copy").put("recursive", true), context);
        assertThat(workspace.resolve("copy")).doesNotExist();
    }

    @Test
    void rejectsPathsOutsideWorkspace() {
        assertThatThrownBy(() -> new FileManageTool().call(
                Json.obj().put("operation", "delete").put("source", "../outside"), ToolContext.of(workspace)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void previewsThenAppliesValidatedBatch() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "old A");
        Files.writeString(workspace.resolve("b.txt"), "old B");
        ArrayNode edits = Json.arr();
        edits.add(Json.obj().put("path", "a.txt").put("oldText", "old").put("newText", "new"));
        edits.add(Json.obj().put("path", "b.txt").put("oldText", "old").put("newText", "new"));
        var args = Json.obj().set("edits", edits);

        String preview = new BatchReplaceTextTool().call(args, ToolContext.of(workspace));
        assertThat(preview).contains("Preview only", "-old A", "+new B");
        assertThat(Files.readString(workspace.resolve("a.txt"))).isEqualTo("old A");

        args.put("apply", true);
        new BatchReplaceTextTool().call(args, ToolContext.of(workspace));
        assertThat(Files.readString(workspace.resolve("a.txt"))).isEqualTo("new A");
        assertThat(Files.readString(workspace.resolve("b.txt"))).isEqualTo("new B");
    }

    @Test
    void invalidEditLeavesEveryFileUntouched() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "old A");
        Files.writeString(workspace.resolve("b.txt"), "old B");
        ArrayNode edits = Json.arr();
        edits.add(Json.obj().put("path", "a.txt").put("oldText", "old").put("newText", "new"));
        edits.add(Json.obj().put("path", "b.txt").put("oldText", "missing").put("newText", "new"));
        var args = Json.obj().set("edits", edits).put("apply", true);

        assertThatThrownBy(() -> new BatchReplaceTextTool().call(args, ToolContext.of(workspace)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(workspace.resolve("a.txt"))).isEqualTo("old A");
        assertThat(Files.readString(workspace.resolve("b.txt"))).isEqualTo("old B");
    }
}
