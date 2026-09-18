package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取文本文件工具。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code path}（string，必填）：文件路径，相对工作目录或工作目录内的绝对路径</li>
 *   <li>{@code startLine}（integer，可选）：起始行号，1 起，默认 1</li>
 *   <li>{@code maxLines}（integer，可选）：最多读取的行数，默认 500</li>
 * </ul>
 *
 * <p>返回：带行号的文本内容，格式为 {@code %4d | 内容}；被截断时会明确提示后续可用
 * {@code startLine} 继续读取。
 *
 * <p>安全边界：路径一律通过 {@link ToolContext#resolve(String)} 解析，越出工作目录会抛
 * {@link SecurityException}。文件不存在抛 {@link FileNotFoundException}，消息中包含解析后的绝对路径。
 */
public class FileReadTool implements Tool {

    private static final int DEFAULT_MAX_LINES = 500;
    private static final int HARD_MAX_LINES = 5000;

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("read_file")
                .description("""
                        读取工作目录内文本文件的内容，返回带行号的文本。大文件可用 startLine 与 maxLines 分页读取。
                        路径必须位于工作目录内。""")
                .parameters(JsonSchema.object()
                        .string("path", "文件路径，例如 src/main/java/App.java")
                        .integer("startLine", "起始行号（1 起），默认 1", false)
                        .integer("maxLines", "最多读取行数，默认 " + DEFAULT_MAX_LINES, false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws IOException {
        String rawPath = ToolSupport.requiredString(arguments, "path");
        int startLine = ToolSupport.optionalInt(arguments, "startLine", 1);
        int maxLines = ToolSupport.optionalInt(arguments, "maxLines", DEFAULT_MAX_LINES);
        if (startLine < 1) {
            throw new IllegalArgumentException("参数 `startLine` 必须从 1 开始，实际为 " + startLine);
        }
        if (maxLines < 1) {
            throw new IllegalArgumentException("参数 `maxLines` 必须大于 0，实际为 " + maxLines);
        }
        maxLines = Math.min(maxLines, HARD_MAX_LINES);

        Path path = context.resolve(rawPath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("文件不存在: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("`" + path + "` 是一个目录，请改用 list_files 工具查看目录内容");
        }

        List<String> lines = new ArrayList<>();
        long totalLines = 0;
        boolean truncated = false;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                if (totalLines < startLine) {
                    continue;
                }
                if (lines.size() >= maxLines) {
                    truncated = true;
                    break;
                }
                lines.add(String.format("%4d | %s", totalLines, line));
            }
        }

        if (lines.isEmpty()) {
            if (totalLines == 0) {
                return "文件 " + path + " 是空文件（0 行）";
            }
            return "文件 " + path + " 共 " + totalLines + " 行，从第 " + startLine + " 行起没有内容";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("文件: ").append(path)
                .append("（显示第 ").append(startLine).append('-')
                .append(startLine + lines.size() - 1).append(" 行）");
        for (String line : lines) {
            sb.append('\n').append(line);
        }
        if (truncated) {
            sb.append("\n...（达到 maxLines 上限，已截断；可用 startLine=")
                    .append(startLine + lines.size())
                    .append(" 继续读取，当前至少共 ").append(totalLines).append(" 行）");
        }
        return sb.toString();
    }
}
