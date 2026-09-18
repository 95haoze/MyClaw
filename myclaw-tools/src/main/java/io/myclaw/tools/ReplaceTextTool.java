package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ReplaceTextTool implements Tool {
    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("replace_text")
                .description("Safely replace an exact text block in one UTF-8 file. The replacement is rejected unless the occurrence count matches expectedOccurrences.")
                .parameters(JsonSchema.object()
                        .string("path", "File path inside the working directory")
                        .string("oldText", "Exact existing text to replace")
                        .string("newText", "Replacement text")
                        .integer("expectedOccurrences", "Required match count; defaults to 1", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws Exception {
        String rawPath = ToolSupport.requiredString(arguments, "path");
        String oldText = ToolSupport.requiredString(arguments, "oldText");
        JsonNode newNode = arguments == null ? null : arguments.get("newText");
        if (newNode == null || newNode.isNull()) throw new IllegalArgumentException("newText is required");
        String newText = newNode.asString();
        int expected = ToolSupport.optionalInt(arguments, "expectedOccurrences", 1);
        if (oldText.isEmpty()) throw new IllegalArgumentException("oldText must not be empty");
        if (expected < 1) throw new IllegalArgumentException("expectedOccurrences must be at least 1");

        Path target = context.resolve(rawPath);
        if (!Files.isRegularFile(target)) throw new java.io.FileNotFoundException("File does not exist: " + target);
        String original = Files.readString(target, StandardCharsets.UTF_8);
        int actual = countOccurrences(original, oldText);
        if (actual != expected) {
            throw new IllegalStateException(
                    "Replacement rejected: expected " + expected + " occurrence(s), found " + actual
            );
        }

        String updated = original.replace(oldText, newText);
        Path parent = target.getParent();
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".myclaw.tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return "Updated " + target + "; replaced " + actual + " occurrence(s).";
    }

    private static int countOccurrences(String content, String value) {
        int count = 0;
        int from = 0;
        while ((from = content.indexOf(value, from)) >= 0) {
            count++;
            from += value.length();
        }
        return count;
    }
}