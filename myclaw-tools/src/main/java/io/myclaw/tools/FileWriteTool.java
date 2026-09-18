package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 写入文本文件工具。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code path}（string，必填）：文件路径，相对工作目录或工作目录内的绝对路径</li>
 *   <li>{@code content}（string，必填）：要写入的文本（UTF-8）</li>
 *   <li>{@code append}（boolean，可选）：true 表示追加，默认 false 覆盖</li>
 * </ul>
 *
 * <p>返回：写入的绝对路径与字符数。父目录不存在时会自动创建。
 *
 * <p>安全边界：路径一律通过 {@link ToolContext#resolve(String)} 解析，越出工作目录会抛
 * {@link SecurityException}。
 */
public class FileWriteTool implements Tool {

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("write_file")
                .description("""
                        把文本写入工作目录内的文件（UTF-8）。父目录会自动创建；
                        append=true 时追加到文件末尾，否则覆盖原内容。路径必须位于工作目录内。""")
                .parameters(JsonSchema.object()
                        .string("path", "文件路径，例如 notes/todo.md")
                        .string("content", "要写入的文本内容")
                        .bool("append", "是否追加写入，默认 false（覆盖）", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws IOException {
        String rawPath = ToolSupport.requiredString(arguments, "path");
        JsonNode contentNode = arguments == null ? null : arguments.get("content");
        if (contentNode == null || contentNode.isNull()) {
            throw new IllegalArgumentException("缺少必需参数 `content`（要写入的文本）");
        }
        String content = contentNode.asString();
        if (content == null) {
            content = "";
        }
        boolean append = ToolSupport.optionalBool(arguments, "append", false);

        Path path = context.resolve(rawPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (append) {
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } else {
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        long size = Files.size(path);
        return "已写入文件: " + path + "\n"
                + "字符数: " + content.length() + "\n"
                + "模式: " + (append ? "追加" : "覆盖") + "\n"
                + "文件当前字节数: " + size;
    }
}
