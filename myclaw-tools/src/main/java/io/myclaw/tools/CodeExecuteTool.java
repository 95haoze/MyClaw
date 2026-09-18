package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class CodeExecuteTool implements Tool {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration MAX_TIMEOUT = Duration.ofSeconds(300);
    private static final int MAX_CODE_CHARS = 200_000;
    private static final int MAX_ARGS = 100;
    private static final int MAX_OUTPUT_BYTES = 1_000_000;
    private static final int MAX_REPORTED_FILES = 100;
    private static final Set<String> SAFE_ENVIRONMENT_KEYS = Set.of(
            "PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "COMSPEC",
            "TEMP", "TMP", "HOME", "USERPROFILE", "LANG", "LC_ALL"
    );

    private final boolean enabled;

    public CodeExecuteTool() {
        this(false);
    }

    public CodeExecuteTool(boolean enabled) {
        this.enabled = enabled;
    }

    /** Kept for source compatibility with the original unfinished class. */
    @Deprecated
    public CodeExecuteTool(Logger ignored) {
        this(false);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("execute_code")
                .description("""
                        Execute a temporary Python, Bash, or Node.js script inside the configured working directory.
                        Use run_command for normal project builds and tests; use execute_code for short generated scripts.
                        Returns JSON containing exitCode, stdout, stderr, timedOut, outputTruncated,
                        generatedFiles, and changedFiles. A non-zero exit code or timeout is a tool failure.
                        The child receives only a small allowlist of non-secret environment variables.
                        This is process isolation, not an operating-system sandbox; keep this tool disabled
                        when running untrusted prompts.
                        """)
                .parameters(JsonSchema.object()
                        .enumOf("language", "Interpreter to use", List.of("python", "bash", "node"))
                        .string("code", "Complete source code to execute")
                        .string("skillName", "Optional directory name under skills/ used as the working directory", false)
                        .string("args", "Optional JSON array of string arguments, or one plain-text argument", false)
                        .integer("timeoutSeconds", "Timeout from 1 to 300 seconds; default 30", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws Exception {
        String language = normalizeLanguage(ToolSupport.requiredString(arguments, "language"));
        String code = ToolSupport.requiredString(arguments, "code");
        if (code.length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("code exceeds " + MAX_CODE_CHARS + " characters");
        }
        int timeoutSeconds = ToolSupport.optionalInt(arguments, "timeoutSeconds", (int) DEFAULT_TIMEOUT.toSeconds());
        if (timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT.toSeconds()) {
            throw new IllegalArgumentException("timeoutSeconds must be between 1 and 300");
        }
        List<String> args = parseArgs(ToolSupport.optionalString(arguments, "args", ""));
        Path workDir = resolveWorkingDirectory(arguments, context);
        Files.createDirectories(workDir);

        Map<String, FileStamp> before = snapshot(workDir);
        Path scriptDirectory = context.resolve(".myclaw-exec");
        Files.createDirectories(scriptDirectory);
        Path script = Files.createTempFile(scriptDirectory, "run-", extension(language));

        Process process = null;
        try {
            Files.writeString(script, code, StandardCharsets.UTF_8);
            List<String> command = new ArrayList<>();
            command.add(interpreter(language));
            command.add(script.toString());
            command.addAll(args);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDir.toFile());
            replaceEnvironment(builder.environment());
            process = builder.start();

            OutputCapture stdout = new OutputCapture(process.getInputStream(), MAX_OUTPUT_BYTES);
            OutputCapture stderr = new OutputCapture(process.getErrorStream(), MAX_OUTPUT_BYTES);
            Thread stdoutThread = Thread.ofVirtual().name("myclaw-code-stdout").start(stdout);
            Thread stderrThread = Thread.ofVirtual().name("myclaw-code-stderr").start(stderr);

            boolean finished;
            try {
                finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Code execution was interrupted", exception);
            }
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            stdoutThread.join(2_000);
            stderrThread.join(2_000);

            int exitCode = finished ? process.exitValue() : -1;
            Map<String, FileStamp> after = snapshot(workDir);
            String result = resultJson(
                    exitCode, stdout.text(), stderr.text(), !finished,
                    stdout.truncated() || stderr.truncated(), before, after
            );
            if (!finished) {
                throw new IOException("Code execution timed out after " + timeoutSeconds + " seconds\n" + result);
            }
            if (exitCode != 0) {
                throw new IOException("Code execution failed with exit code " + exitCode + "\n" + result);
            }
            return result;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(script);
        }
    }

    private static Path resolveWorkingDirectory(JsonNode arguments, ToolContext context) throws IOException {
        String skillName = ToolSupport.optionalString(arguments, "skillName", "");
        if (skillName.isBlank()) return context.workingDirectory();
        if (!skillName.matches("[A-Za-z0-9._-]+") || ".".equals(skillName) || "..".equals(skillName)) {
            throw new IllegalArgumentException("skillName contains unsupported characters");
        }
        Path skillDirectory = context.resolve("skills/" + skillName);
        if (!Files.isDirectory(skillDirectory)) {
            throw new java.io.FileNotFoundException("Skill directory does not exist: " + skillDirectory);
        }
        return skillDirectory;
    }

    private static String normalizeLanguage(String language) {
        String normalized = language.strip().toLowerCase(Locale.ROOT);
        if (!List.of("python", "bash", "node").contains(normalized)) {
            throw new IllegalArgumentException("language must be python, bash, or node");
        }
        return normalized;
    }

    private static String interpreter(String language) {
        return switch (language) {
            case "python" -> System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "python.exe" : "python3";
            case "bash" -> "bash";
            case "node" -> System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "node.exe" : "node";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private static String extension(String language) {
        return switch (language) {
            case "python" -> ".py";
            case "bash" -> ".sh";
            case "node" -> ".mjs";
            default -> ".txt";
        };
    }

    private static List<String> parseArgs(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String value = raw.strip();
        if (!value.startsWith("[")) return List.of(value);
        JsonNode parsed;
        try {
            parsed = Json.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("args must be a JSON string array or plain text", exception);
        }
        if (!parsed.isArray()) throw new IllegalArgumentException("args JSON must be an array");
        if (parsed.size() > MAX_ARGS) throw new IllegalArgumentException("args exceeds " + MAX_ARGS + " items");
        List<String> result = new ArrayList<>(parsed.size());
        for (JsonNode item : parsed) {
            if (!item.isString()) throw new IllegalArgumentException("every args item must be a string");
            result.add(item.asString());
        }
        return List.copyOf(result);
    }

    private static void replaceEnvironment(Map<String, String> target) {
        Map<String, String> inherited = new LinkedHashMap<>(target);
        target.clear();
        inherited.forEach((key, value) -> {
            if (SAFE_ENVIRONMENT_KEYS.contains(key.toUpperCase(Locale.ROOT))) target.put(key, value);
        });
    }

    private static Map<String, FileStamp> snapshot(Path root) throws IOException {
        Map<String, FileStamp> files = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(root.resolve(".myclaw-exec")))
                    .limit(20_000)
                    .forEach(path -> {
                        try {
                            String relative = root.relativize(path).toString().replace('\\', '/');
                            files.put(relative, new FileStamp(Files.size(path), Files.getLastModifiedTime(path)));
                        } catch (IOException ignored) {
                            // A concurrently removed file is simply omitted from the report.
                        }
                    });
        }
        return files;
    }

    private static String resultJson(
            int exitCode,
            String stdout,
            String stderr,
            boolean timedOut,
            boolean truncated,
            Map<String, FileStamp> before,
            Map<String, FileStamp> after
    ) {
        ObjectNode result = Json.obj();
        result.put("exitCode", exitCode);
        result.put("stdout", stdout);
        result.put("stderr", stderr);
        result.put("timedOut", timedOut);
        result.put("outputTruncated", truncated);
        ArrayNode generated = Json.arr();
        ArrayNode changed = Json.arr();
        after.forEach((name, stamp) -> {
            FileStamp old = before.get(name);
            if (old == null && generated.size() < MAX_REPORTED_FILES) generated.add(name);
            else if (old != null && !old.equals(stamp) && changed.size() < MAX_REPORTED_FILES) changed.add(name);
        });
        result.set("generatedFiles", generated);
        result.set("changedFiles", changed);
        return Json.write(result);
    }

    private record FileStamp(long size, FileTime modifiedAt) {
    }

    private static final class OutputCapture implements Runnable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile boolean truncated;

        private OutputCapture(InputStream input, int limit) {
            this.input = input;
            this.limit = limit;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8_192];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int remaining = limit - output.size();
                    if (remaining > 0) output.write(buffer, 0, Math.min(read, remaining));
                    if (read > remaining) truncated = true;
                }
            } catch (IOException ignored) {
                // Process destruction closes streams during timeout and cancellation.
            }
        }

        private String text() {
            return output.toString(StandardCharsets.UTF_8);
        }

        private boolean truncated() {
            return truncated;
        }
    }
}