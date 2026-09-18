package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a multi-file edit plan, previews it as a diff, and optionally applies it.
 */
public final class BatchReplaceTextTool implements Tool {
    private record Change(Path path, String before, String after, int replacements) {
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode edit = JsonSchema.object()
                .string("path", "UTF-8 file path inside the workspace")
                .string("oldText", "Exact existing text")
                .string("newText", "Replacement text")
                .integer("expectedOccurrences", "Expected match count; defaults to 1", false)
                .build();
        return ToolDefinition.builder("batch_replace_text")
                .description("Validate exact replacements across multiple files. Defaults to preview-only unified diff; set apply=true to write only after every edit validates.")
                .parameters(JsonSchema.object()
                        .arrayOf("edits", "Ordered replacement operations", edit, true)
                        .bool("apply", "Apply the validated plan; defaults to false", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws Exception {
        JsonNode edits = arguments == null ? null : arguments.get("edits");
        if (edits == null || !edits.isArray() || edits.isEmpty()) {
            throw new IllegalArgumentException("edits must be a non-empty array");
        }
        Map<Path, String> originals = new LinkedHashMap<>();
        Map<Path, String> updated = new LinkedHashMap<>();
        Map<Path, Integer> counts = new LinkedHashMap<>();
        for (JsonNode edit : edits) {
            Path path = context.resolve(ToolSupport.requiredString(edit, "path"));
            if (!Files.isRegularFile(path)) throw new java.io.FileNotFoundException("File does not exist: " + path);
            String oldText = ToolSupport.requiredString(edit, "oldText");
            JsonNode newNode = edit.get("newText");
            if (newNode == null || newNode.isNull())
                throw new IllegalArgumentException("newText is required for " + path);
            String current = updated.computeIfAbsent(path, item -> read(item));
            originals.putIfAbsent(path, current);
            int expected = ToolSupport.optionalInt(edit, "expectedOccurrences", 1);
            int actual = count(current, oldText);
            if (actual != expected)
                throw new IllegalStateException("Edit rejected for " + path + ": expected " + expected + " occurrence(s), found " + actual);
            updated.put(path, current.replace(oldText, newNode.asString()));
            counts.merge(path, actual, Integer::sum);
        }

        String diff = renderDiff(originals, updated);
        if (!ToolSupport.optionalBool(arguments, "apply", false)) return "Preview only; no files changed.\n" + diff;

        List<Path> written = new ArrayList<>();
        try {
            for (var entry : updated.entrySet()) {
                Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
                written.add(entry.getKey());
            }
        } catch (Exception failure) {
            for (Path path : written) Files.writeString(path, originals.get(path), StandardCharsets.UTF_8);
            throw failure;
        }
        return "Applied " + edits.size() + " edit(s) to " + updated.size() + " file(s).\n" + diff;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private static int count(String text, String value) {
        if (value.isEmpty()) throw new IllegalArgumentException("oldText must not be empty");
        int count = 0, offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static String renderDiff(Map<Path, String> before, Map<Path, String> after) {
        StringBuilder output = new StringBuilder();
        for (Path path : before.keySet()) {
            output.append("--- a/").append(path.getFileName()).append('\n')
                    .append("+++ b/").append(path.getFileName()).append('\n');
            String[] oldLines = before.get(path).split("\\R", -1);
            String[] newLines = after.get(path).split("\\R", -1);
            int prefix = 0;
            while (prefix < oldLines.length && prefix < newLines.length && oldLines[prefix].equals(newLines[prefix]))
                prefix++;
            int oldEnd = oldLines.length - 1, newEnd = newLines.length - 1;
            while (oldEnd >= prefix && newEnd >= prefix && oldLines[oldEnd].equals(newLines[newEnd])) {
                oldEnd--;
                newEnd--;
            }
            output.append("@@ -").append(prefix + 1).append(',').append(Math.max(0, oldEnd - prefix + 1))
                    .append(" +").append(prefix + 1).append(',').append(Math.max(0, newEnd - prefix + 1)).append(" @@\n");
            for (int i = prefix; i <= oldEnd; i++) output.append('-').append(oldLines[i]).append('\n');
            for (int i = prefix; i <= newEnd; i++) output.append('+').append(newLines[i]).append('\n');
        }
        return ToolSupport.truncate(output.toString(), 30_000);
    }
}
