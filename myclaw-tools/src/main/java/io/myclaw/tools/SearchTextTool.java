package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class SearchTextTool implements Tool {
    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final int HARD_MAX_RESULTS = 500;
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> SKIPPED_DIRECTORIES =
            Set.of(".git", "target", "node_modules", "dist", ".idea", ".vscode");

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("search_text")
                .description("Search text files in the working directory. Returns path, line number and matching line. Use this before editing code.")
                .parameters(JsonSchema.object()
                        .string("query", "Literal text to search for")
                        .string("path", "Directory or file to search; defaults to .", false)
                        .string("glob", "File name glob such as *.java; defaults to *", false)
                        .bool("caseSensitive", "Whether matching is case-sensitive; defaults to false", false)
                        .integer("maxResults", "Maximum matches; defaults to 100 and is capped at 500", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws Exception {
        String query = ToolSupport.requiredString(arguments, "query");
        if (query.isEmpty()) throw new IllegalArgumentException("query must not be empty");
        String rawPath = ToolSupport.optionalString(arguments, "path", ".");
        String glob = ToolSupport.optionalString(arguments, "glob", "*");
        boolean caseSensitive = ToolSupport.optionalBool(arguments, "caseSensitive", false);
        int maxResults = Math.min(
                Math.max(1, ToolSupport.optionalInt(arguments, "maxResults", DEFAULT_MAX_RESULTS)),
                HARD_MAX_RESULTS
        );

        Path root = context.resolve(rawPath);
        if (!Files.exists(root)) throw new java.io.FileNotFoundException("Search path does not exist: " + root);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        String expected = caseSensitive ? query : query.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        int matches = 0;

        try (Stream<Path> paths = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !isSkipped(root, path))
                    .filter(path -> matcher.matches(path.getFileName()))
                    .filter(path -> isSearchable(path))
                    .sorted()
                    .toList();

            for (Path file : files) {
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    int lineNumber = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        String candidate = caseSensitive ? line : line.toLowerCase(Locale.ROOT);
                        if (candidate.contains(expected)) {
                            if (matches++ > 0) result.append('\n');
                            result.append(context.workingDirectory().relativize(file.toAbsolutePath().normalize()))
                                    .append(':').append(lineNumber).append(": ").append(line.strip());
                            if (matches >= maxResults) {
                                return result.append("\n... results truncated at ").append(maxResults).toString();
                            }
                        }
                    }
                } catch (java.nio.charset.MalformedInputException ignored) {
                    // Binary or non UTF-8 files are outside this text-search tool's contract.
                }
            }
        }
        return matches == 0 ? "No matches found." : result.toString();
    }

    private static boolean isSkipped(Path root, Path file) {
        Path relative = Files.isDirectory(root) ? root.relativize(file) : file.getFileName();
        for (Path part : relative) {
            if (SKIPPED_DIRECTORIES.contains(part.toString())) return true;
        }
        return false;
    }

    private static boolean isSearchable(Path path) {
        try {
            return Files.size(path) <= MAX_FILE_BYTES;
        } catch (java.io.IOException exception) {
            return false;
        }
    }
}