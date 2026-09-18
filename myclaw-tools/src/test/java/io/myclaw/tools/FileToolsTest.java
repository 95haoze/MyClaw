package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件类工具（读 / 写 / 列目录）的单元测试：全部在 {@link TempDir} 沙箱内进行，
 * 并验证路径越界会被 {@link ToolContext#resolve(String)} 拦截。
 */
class FileToolsTest {

    @TempDir
    Path tempDir;

    private final FileReadTool readTool = new FileReadTool();
    private final FileWriteTool writeTool = new FileWriteTool();
    private final FileListTool listTool = new FileListTool();

    private ToolContext context() {
        return ToolContext.of(tempDir);
    }

    @Test
    @DisplayName("写入后能原样读回，返回绝对路径与字符数")
    void writesThenReadsBack() throws Exception {
        ToolContext context = context();

        String writeResult = writeTool.call(
                Json.obj().put("path", "notes/a.txt").put("content", "line1\nline2"), context);

        assertThat(writeResult)
                .contains("已写入文件")
                .contains(tempDir.resolve("notes/a.txt").toString())
                .contains("字符数: 11")
                .contains("覆盖");
        assertThat(Files.readString(tempDir.resolve("notes/a.txt"))).isEqualTo("line1\nline2");

        String readResult = readTool.call(Json.obj().put("path", "notes/a.txt"), context);

        assertThat(readResult).contains("1 | line1").contains("2 | line2");
    }

    @Test
    @DisplayName("append=true 时追加而不是覆盖")
    void appendsToExistingFile() throws Exception {
        ToolContext context = context();
        writeTool.call(Json.obj().put("path", "log.txt").put("content", "A"), context);

        String result = writeTool.call(
                Json.obj().put("path", "log.txt").put("content", "B").put("append", true), context);

        assertThat(result).contains("追加");
        assertThat(Files.readString(tempDir.resolve("log.txt"))).isEqualTo("AB");
    }

    @Test
    @DisplayName("父目录自动创建")
    void createsParentDirectories() throws Exception {
        writeTool.call(Json.obj().put("path", "a/b/c/d.txt").put("content", "deep"), context());

        assertThat(Files.readString(tempDir.resolve("a/b/c/d.txt"))).isEqualTo("deep");
    }

    @Test
    @DisplayName("越界路径必须抛 SecurityException")
    void rejectsPathTraversal() {
        ToolContext context = context();

        assertThatThrownBy(() -> readTool.call(Json.obj().put("path", "../x.txt"), context))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> writeTool.call(Json.obj().put("path", "../x.txt").put("content", "x"), context))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> listTool.call(Json.obj().put("path", ".."), context))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> readTool.call(Json.obj().put("path", "a/../../x.txt"), context))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("文件不存在时抛出带绝对路径的 FileNotFoundException")
    void reportsMissingFile() {
        Path missing = tempDir.resolve("nope.txt");

        assertThatThrownBy(() -> readTool.call(Json.obj().put("path", "nope.txt"), context()))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    @DisplayName("startLine 与 maxLines 生效")
    void honoursStartLineAndMaxLines() throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            content.append("row").append(i).append('\n');
        }
        Files.writeString(tempDir.resolve("rows.txt"), content.toString());

        String result = readTool.call(
                Json.obj().put("path", "rows.txt").put("startLine", 3).put("maxLines", 2), context());

        assertThat(result).contains("3 | row3").contains("4 | row4").doesNotContain("row5");
    }

    @Test
    @DisplayName("超出 maxLines 时截断并提示续读")
    void hintsWhenTruncated() throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            content.append("row").append(i).append('\n');
        }
        Files.writeString(tempDir.resolve("rows.txt"), content.toString());

        String result = readTool.call(Json.obj().put("path", "rows.txt").put("maxLines", 3), context());

        assertThat(result).contains("截断").contains("startLine=4");
    }

    @Test
    @DisplayName("分页参数非法时报错")
    void rejectsIllegalPagingArguments() {
        assertThatThrownBy(() -> readTool.call(Json.obj().put("path", "a.txt").put("startLine", 0), context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startLine");
        assertThatThrownBy(() -> readTool.call(Json.obj().put("path", "a.txt").put("maxLines", 0), context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLines");
    }

    @Test
    @DisplayName("空文件与超出范围的行号都有明确提示")
    void reportsEmptyFileAndOutOfRangeStart() throws Exception {
        Files.writeString(tempDir.resolve("empty.txt"), "");
        Files.writeString(tempDir.resolve("one.txt"), "only\n");

        assertThat(readTool.call(Json.obj().put("path", "empty.txt"), context())).contains("空文件");
        assertThat(readTool.call(Json.obj().put("path", "one.txt").put("startLine", 99), context()))
                .contains("没有内容");
    }

    @Test
    @DisplayName("目录列出：目录带 / 后缀，文件带字节数")
    void listsDirectoryEntries() throws Exception {
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("a.txt"), "hello");

        String result = listTool.call(Json.obj(), context());

        assertThat(result).contains("sub/").contains("a.txt").contains("(5 字节)").contains("2 项");
        // 目录排在文件之前
        assertThat(result.indexOf("sub/")).isLessThan(result.indexOf("a.txt"));
    }

    @Test
    @DisplayName("recursive=true 时递归列出子目录")
    void listsRecursively() throws Exception {
        Files.createDirectories(tempDir.resolve("sub/inner"));
        Files.writeString(tempDir.resolve("sub/inner/deep.txt"), "x");

        assertThat(listTool.call(Json.obj(), context())).contains("sub/").doesNotContain("deep.txt");

        String recursive = listTool.call(Json.obj().put("recursive", true), context());

        assertThat(recursive).contains("sub/inner/").contains("sub/inner/deep.txt");
    }

    @Test
    @DisplayName("空目录返回明确提示")
    void reportsEmptyDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("empty"));

        assertThat(listTool.call(Json.obj().put("path", "empty"), context())).contains("为空");
    }

    @Test
    @DisplayName("目录不存在或路径是文件时给出可纠正的错误")
    void reportsMissingDirectoryAndFileTarget() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "x");
        Path missing = tempDir.resolve("nope");

        assertThatThrownBy(() -> listTool.call(Json.obj().put("path", "nope"), context()))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining(missing.toString());
        assertThatThrownBy(() -> listTool.call(Json.obj().put("path", "a.txt"), context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read_file");
    }

    @Test
    @DisplayName("缺少必填参数时报错")
    void requiresMandatoryParameters() {
        assertThatThrownBy(() -> readTool.call(Json.obj(), context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
        assertThatThrownBy(() -> writeTool.call(Json.obj().put("path", "a.txt"), context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }
}
