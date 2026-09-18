package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.util.List;

/** Guarded workspace file and directory operations. */
public final class FileManageTool implements Tool {
    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("manage_file")
                .description("Create directories, copy, move/rename, or delete files and directories inside the workspace. Recursive directory deletion requires recursive=true.")
                .parameters(JsonSchema.object()
                        .enumOf("operation", "Operation to perform", List.of("mkdir", "copy", "move", "delete"))
                        .string("source", "Source path inside the workspace", false)
                        .string("destination", "Destination path for copy or move", false)
                        .bool("recursive", "Allow recursive directory copy/delete", false)
                        .bool("overwrite", "Allow replacing an existing destination", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws Exception {
        String operation = ToolSupport.requiredString(arguments, "operation");
        boolean recursive = ToolSupport.optionalBool(arguments, "recursive", false);
        boolean overwrite = ToolSupport.optionalBool(arguments, "overwrite", false);
        return switch (operation) {
            case "mkdir" -> mkdir(context.resolve(ToolSupport.requiredString(arguments, "source")));
            case "copy" -> copy(context.resolve(ToolSupport.requiredString(arguments, "source")),
                    context.resolve(ToolSupport.requiredString(arguments, "destination")), recursive, overwrite);
            case "move" -> move(context.resolve(ToolSupport.requiredString(arguments, "source")),
                    context.resolve(ToolSupport.requiredString(arguments, "destination")), overwrite);
            case "delete" -> delete(context.resolve(ToolSupport.requiredString(arguments, "source")), recursive);
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    private static String mkdir(Path path) throws IOException {
        Files.createDirectories(path);
        return "Created directory: " + path;
    }

    private static String move(Path source, Path destination, boolean overwrite) throws IOException {
        requireExisting(source);
        createParent(destination);
        if (!overwrite && Files.exists(destination)) throw new IllegalStateException("Destination exists: " + destination);
        if (overwrite) Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        else Files.move(source, destination);
        return "Moved " + source + " -> " + destination;
    }

    private static String copy(Path source, Path destination, boolean recursive, boolean overwrite) throws IOException {
        requireExisting(source);
        if (Files.isDirectory(source)) {
            if (!recursive) throw new IllegalArgumentException("Copying a directory requires recursive=true");
            try (var paths = Files.walk(source)) {
                for (Path item : paths.toList()) {
                    Path target = destination.resolve(source.relativize(item));
                    if (Files.isDirectory(item)) Files.createDirectories(target);
                    else copyFile(item, target, overwrite);
                }
            }
        } else copyFile(source, destination, overwrite);
        return "Copied " + source + " -> " + destination;
    }

    private static void copyFile(Path source, Path destination, boolean overwrite) throws IOException {
        createParent(destination);
        if (!overwrite && Files.exists(destination)) throw new IllegalStateException("Destination exists: " + destination);
        if (overwrite) Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        else Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static String delete(Path path, boolean recursive) throws IOException {
        requireExisting(path);
        if (Files.isDirectory(path) && recursive) {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.delete(dir); return FileVisitResult.CONTINUE;
                }
            });
        } else {
            try { Files.delete(path); }
            catch (DirectoryNotEmptyException exception) {
                throw new IllegalArgumentException("Directory is not empty; set recursive=true to delete it", exception);
            }
        }
        return "Deleted: " + path;
    }

    private static void requireExisting(Path path) throws IOException {
        if (!Files.exists(path)) throw new java.io.FileNotFoundException("Path does not exist: " + path);
    }

    private static void createParent(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
    }
}
