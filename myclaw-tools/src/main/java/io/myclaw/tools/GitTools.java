package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Structured Git tools. Commands are executed directly without a shell.
 */
public final class GitTools {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_OUTPUT = 40_000;

    private GitTools() {
    }

    public static Tool status() {
        return tool("git_status", "Show branch and working-tree status in a stable machine-readable format.",
                JsonSchema.object().bool("includeUntracked", "Include untracked files; defaults to true", false).build(),
                (args, context) -> runInRepo(context, List.of("status", "--short", "--branch",
                        ToolSupport.optionalBool(args, "includeUntracked", true) ? "--untracked-files=all" : "--untracked-files=no")));
    }

    public static Tool diff() {
        return tool("git_diff", "Show unstaged or staged changes without external diff programs.",
                JsonSchema.object().bool("staged", "Show staged changes", false)
                        .string("path", "Optional workspace-relative path", false).build(),
                (args, context) -> {
                    List<String> command = new ArrayList<>(List.of("diff", "--no-ext-diff", "--no-color"));
                    if (ToolSupport.optionalBool(args, "staged", false)) command.add("--cached");
                    addOptionalPath(command, args, context);
                    return runInRepo(context, command);
                });
    }

    public static Tool log() {
        return tool("git_log", "Show recent commit history with commit id, author, date, and subject.",
                JsonSchema.object().integer("limit", "Number of commits, 1-100; defaults to 20", false)
                        .string("path", "Optional workspace-relative path", false).build(),
                (args, context) -> {
                    int limit = ToolSupport.optionalInt(args, "limit", 20);
                    if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
                    List<String> command = new ArrayList<>(List.of("log", "-n", String.valueOf(limit),
                            "--date=iso-strict", "--pretty=format:%H%x09%an%x09%ad%x09%s"));
                    addOptionalPath(command, args, context);
                    return runInRepo(context, command);
                });
    }

    public static Tool add() {
        return tool("git_add", "Stage explicit workspace paths. Use paths=['.'] to stage all workspace changes.",
                JsonSchema.object().arrayOfStrings("paths", "Paths to stage").build(),
                (args, context) -> {
                    List<String> paths = requiredPaths(args, context);
                    List<String> command = new ArrayList<>(List.of("add", "--"));
                    command.addAll(paths);
                    return runInRepo(context, command);
                });
    }

    public static Tool reset() {
        return tool("git_reset", "Safely unstage explicit paths while preserving working-tree content. This tool never performs --hard.",
                JsonSchema.object().arrayOfStrings("paths", "Paths to unstage; use ['.'] for all staged paths").build(),
                (args, context) -> {
                    List<String> command = new ArrayList<>(List.of("reset", "--mixed", "HEAD", "--"));
                    command.addAll(requiredPaths(args, context));
                    return runInRepo(context, command);
                });
    }

    public static Tool commit() {
        return tool("git_commit", "Commit currently staged changes. It never stages files automatically.",
                JsonSchema.object().string("message", "Commit message").build(),
                (args, context) -> {
                    String message = ToolSupport.requiredString(args, "message");
                    if (message.length() > 5000)
                        throw new IllegalArgumentException("message must not exceed 5000 characters");
                    return runInRepo(context, List.of("commit", "--no-gpg-sign", "-m", message));
                });
    }

    public static Tool branch() {
        return tool("git_branch", "List, create, or safely delete local branches.",
                JsonSchema.object().enumOf("action", "Branch operation", List.of("list", "create", "delete"))
                        .string("name", "Branch name for create/delete", false)
                        .bool("force", "Force branch deletion with -D", false).build(),
                (args, context) -> {
                    String action = ToolSupport.requiredString(args, "action");
                    if ("list".equals(action)) return runInRepo(context, List.of("branch", "--list", "--no-color"));
                    String name = branchName(args);
                    if ("create".equals(action)) return runInRepo(context, List.of("branch", name));
                    if ("delete".equals(action)) return runInRepo(context,
                            List.of("branch", ToolSupport.optionalBool(args, "force", false) ? "-D" : "-d", name));
                    throw new IllegalArgumentException("Unsupported branch action: " + action);
                });
    }

    public static Tool checkout() {
        return tool("git_checkout", "Switch to a local branch, optionally creating it. Force overwrite is intentionally unsupported.",
                JsonSchema.object().string("branch", "Local branch name")
                        .bool("create", "Create and switch to a new branch", false).build(),
                (args, context) -> {
                    String branch = branchName(args, "branch");
                    return runInRepo(context, ToolSupport.optionalBool(args, "create", false)
                            ? List.of("switch", "-c", branch) : List.of("switch", branch));
                });
    }

    private static Tool tool(String name, String description, JsonNode schema, Call call) {
        ToolDefinition definition = ToolDefinition.builder(name).description(description).parameters(schema).build();
        return new Tool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public String call(JsonNode arguments, ToolContext context) throws Exception {
                return call.run(arguments, context);
            }
        };
    }

    private static String runInRepo(ToolContext context, List<String> arguments) throws Exception {
        Path repo = repository(context);
        return execute(repo, arguments);
    }

    private static Path repository(ToolContext context) throws Exception {
        Path workspace = context.workingDirectory().toAbsolutePath().normalize();
        String output = execute(workspace, List.of("rev-parse", "--show-toplevel")).strip();
        Path root = Path.of(output).toAbsolutePath().normalize();
        if (!root.startsWith(workspace)) {
            throw new SecurityException("Git repository root is outside the configured workspace: " + root);
        }
        return root;
    }

    private static String execute(Path directory, List<String> arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        FutureTask<byte[]> outputTask = new FutureTask<>(() -> process.getInputStream().readAllBytes());
        Thread.ofVirtual().name("myclaw-git-output").start(outputTask);
        boolean finished = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            outputTask.cancel(true);
            throw new IllegalStateException("Git command timed out after " + TIMEOUT.toSeconds() + " seconds");
        }
        String output = new String(outputTask.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        output = ToolSupport.truncate(output, MAX_OUTPUT);
        if (process.exitValue() != 0)
            throw new IllegalStateException("Git failed (exit " + process.exitValue() + "): " + output.strip());
        return output.isBlank() ? "Git command completed successfully (no output)." : output.stripTrailing();
    }

    private static void addOptionalPath(List<String> command, JsonNode args, ToolContext context) {
        String path = ToolSupport.optionalString(args, "path", "");
        if (!path.isBlank()) {
            command.add("--");
            command.add(literalPath(context, path));
        }
    }

    private static List<String> requiredPaths(JsonNode args, ToolContext context) {
        JsonNode paths = args == null ? null : args.get("paths");
        if (paths == null || !paths.isArray() || paths.isEmpty())
            throw new IllegalArgumentException("paths must be a non-empty array");
        List<String> result = new ArrayList<>();
        for (JsonNode path : paths) {
            if (!path.isString() || path.asString().isBlank())
                throw new IllegalArgumentException("Every path must be a non-empty string");
            result.add(literalPath(context, path.asString()));
        }
        return result;
    }

    private static String literalPath(ToolContext context, String raw) {
        if (".".equals(raw)) return ".";
        Path path = context.resolve(raw);
        String relative = context.workingDirectory().relativize(path).toString().replace('\\', '/');
        return ":(literal)" + relative;
    }

    private static String branchName(JsonNode args) {
        return branchName(args, "name");
    }

    private static String branchName(JsonNode args, String field) {
        String name = ToolSupport.requiredString(args, field);
        if (name.length() > 255 || name.startsWith("-") || !name.matches("[A-Za-z0-9][A-Za-z0-9._/-]*") ||
                name.contains("..") || name.contains("//") || name.endsWith("/") || name.endsWith(".")) {
            throw new IllegalArgumentException("Invalid branch name: " + name);
        }
        return name;
    }

    @FunctionalInterface
    private interface Call {
        String run(JsonNode arguments, ToolContext context) throws Exception;
    }
}
