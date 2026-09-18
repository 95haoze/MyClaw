package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 列出目录内容工具。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code path}（string，可选）：目录路径，默认 {@code .}（工作目录本身）</li>
 *   <li>{@code recursive}（boolean，可选）：是否递归列出子目录，默认 false</li>
 * </ul>
 *
 * <p>返回：目录项清单。目录名带 {@code /} 后缀，文件显示字节数；目录为空时返回明确提示。
 * 路径不存在抛 {@link FileNotFoundException}，路径是文件则给出改用 read_file 的提示。
 *
 * <p>安全边界：路径一律通过 {@link ToolContext#resolve(String)} 解析，越出工作目录会抛
 * {@link SecurityException}。
 */
public class FileListTool implements Tool {

    /** 单次列举的最大条目数，避免在大目录上刷屏。 */
    private static final int MAX_ENTRIES = 1000;

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("list_files")
                .description("""
                        列出工作目录内某个目录的文件与子目录。目录名以 / 结尾，文件后附带字节数。
                        recursive=true 时递归列出所有层级。用于了解项目结构。""")
                .parameters(JsonSchema.object()
                        .string("path", "目录路径，默认当前工作目录 .", false)
                        .bool("recursive", "是否递归列出子目录，默认 false", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws IOException {
        String rawPath = ToolSupport.optionalString(arguments, "path", ".");
        boolean recursive = ToolSupport.optionalBool(arguments, "recursive", false);

        Path directory = context.resolve(rawPath);
        if (!Files.exists(directory)) {
            throw new FileNotFoundException("目录不存在: " + directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("`" + directory + "` 不是目录，请改用 read_file 工具读取文件内容");
        }

        List<Entry> entries = new ArrayList<>();
        boolean truncated = false;
        try (Stream<Path> stream = recursive ? Files.walk(directory) : Files.list(directory)) {
            List<Path> paths = stream
                    .filter(path -> !path.equals(directory))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path path : paths) {
                if (entries.size() >= MAX_ENTRIES) {
                    truncated = true;
                    break;
                }
                String relative = directory.relativize(path).toString().replace('\\', '/');
                boolean isDirectory = Files.isDirectory(path);
                long size = isDirectory ? -1L : Files.size(path);
                entries.add(new Entry(relative + (isDirectory ? "/" : ""), isDirectory, size));
            }
        }

        if (entries.isEmpty()) {
            return "目录 " + directory + " 为空（没有文件或子目录）";
        }
        // 目录在前、文件在后，各自按名称排序
        entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::name));

        StringBuilder sb = new StringBuilder();
        sb.append("目录: ").append(directory).append("（").append(entries.size()).append(" 项）");
        for (Entry entry : entries) {
            sb.append('\n').append(entry.name());
            if (!entry.directory()) {
                sb.append("  (").append(entry.size()).append(" 字节)");
            }
        }
        if (truncated) {
            sb.append("\n...（条目数超过 ").append(MAX_ENTRIES).append(" 上限，已截断）");
        }
        return sb.toString();
    }

    /** 一个目录项的展示数据。 */
    private record Entry(String name, boolean directory, long size) {
    }
}
