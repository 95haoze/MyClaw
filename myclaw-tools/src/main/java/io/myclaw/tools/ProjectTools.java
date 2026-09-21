package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Structured project detection, build, test, and lint tools.
 */
public final class ProjectTools {
    private static final int MAX_OUTPUT = 50_000;

    private ProjectTools() {
    }

    public static Tool detect() {
        return tool("detect_project", "Detect supported build systems and their standard commands without executing them.",
                JsonSchema.object().string("path", "Project directory; defaults to .", false).build(),
                (args, context) -> {
                    Path directory = directory(args, context);
                    List<Project> projects = detectProjects(directory);
                    if (projects.isEmpty())
                        return "No supported Maven, Gradle, npm, pnpm, or yarn project found in " + context.workingDirectory().relativize(directory);
                    return projects.stream().map(p -> p.type + "\t" + context.workingDirectory().relativize(p.directory) + "\tbuild=" + String.join(" ", p.build)).reduce((a, b) -> a + "\n" + b).orElseThrow();
                });
    }

    public static Tool build() {
        return operation("build_project", "Run the detected project's standard build command.", Operation.BUILD);
    }

    public static Tool tests() {
        return operation("run_tests", "Run the detected project's test suite.", Operation.TEST);
    }

    public static Tool lint() {
        return operation("lint_project", "Run the detected project's configured lint/check command.", Operation.LINT);
    }

    public static Tool testCase() {
        return tool("run_test_case", "Run one test class or test selector in a detected Maven or Gradle project.",
                JsonSchema.object().string("test", "Test selector, for example com.example.FooTest or FooTest#method")
                        .string("path", "Project directory; defaults to .", false)
                        .integer("timeoutSeconds", "Timeout from 1-1800; defaults to 300", false).build(),
                (args, context) -> {
                    Project project = project(args, context);
                    String selector = ToolSupport.requiredString(args, "test");
                    if (!selector.matches("[A-Za-z0-9_.$#*\n[\n]-]+"))
                        throw new IllegalArgumentException("Invalid test selector: " + selector);
                    List<String> command = switch (project.type) {
                        case "maven" -> append(project.base(), "-Dtest=" + selector, "test");
                        case "gradle" -> append(project.base(), "test", "--tests", selector);
                        default ->
                                throw new IllegalArgumentException("run_test_case currently supports Maven and Gradle projects only");
                    };
                    return execute(project.directory, command, timeout(args, 300));
                });
    }

    private static Tool operation(String name, String description, Operation operation) {
        return tool(name, description, JsonSchema.object()
                        .string("path", "Project directory; defaults to .", false)
                        .integer("timeoutSeconds", "Timeout from 1-1800 seconds", false).build(),
                (args, context) -> {
                    Project project = project(args, context);
                    List<String> command = switch (operation) {
                        case BUILD -> project.build;
                        case TEST -> project.test;
                        case LINT -> project.lint;
                    };
                    if (command.isEmpty())
                        throw new IllegalStateException("No " + operation.name().toLowerCase() + " command configured for " + project.type);
                    return execute(project.directory, command, timeout(args, operation == Operation.BUILD ? 600 : 300));
                });
    }

    private static Project project(JsonNode args, ToolContext context) {
        Path directory = directory(args, context);
        List<Project> projects = detectProjects(directory);
        if (projects.isEmpty()) throw new IllegalArgumentException("No supported project found at " + directory);
        if (projects.size() > 1)
            throw new IllegalArgumentException("Multiple project types found at " + directory + "; specify a more precise path");
        return projects.getFirst();
    }

    private static Path directory(JsonNode args, ToolContext context) {
        Path path = context.resolve(ToolSupport.optionalString(args, "path", "."));
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("path must be an existing directory: " + path);
        return path;
    }

    private static List<Project> detectProjects(Path d) {
        List<Project> found = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (Files.exists(d.resolve("pom.xml"))) {
            List<String> base = Files.exists(d.resolve(windows ? "mvnw.cmd" : "mvnw")) ? List.of(d.resolve(windows ? "mvnw.cmd" : "mvnw").toString()) : List.of("mvn");
            found.add(new Project("maven", d, append(base, "-B", "verify"), append(base, "-B", "test"), append(base, "-B", "verify"), base));
        }
        if (Files.exists(d.resolve("build.gradle")) || Files.exists(d.resolve("build.gradle.kts"))) {
            List<String> base = Files.exists(d.resolve(windows ? "gradlew.bat" : "gradlew")) ? List.of(d.resolve(windows ? "gradlew.bat" : "gradlew").toString()) : List.of("gradle");
            found.add(new Project("gradle", d, append(base, "build"), append(base, "test"), append(base, "check"), base));
        }
        if (Files.exists(d.resolve("package.json"))) {
            String manager = Files.exists(d.resolve("pnpm-lock.yaml")) ? "pnpm" : Files.exists(d.resolve("yarn.lock")) ? "yarn" : "npm";
            List<String> prefix = "npm".equals(manager) ? List.of("npm", "run") : List.of(manager);
            found.add(new Project(manager, d, append(prefix, "build"), "npm".equals(manager) ? List.of("npm", "test") : List.of(manager, "test"), append(prefix, "lint"), prefix));
        }
        return found;
    }

    private static List<String> append(List<String> base, String... values) {
        List<String> result = new ArrayList<>(base);
        result.addAll(List.of(values));
        return result;
    }

    private static int timeout(JsonNode args, int defaultValue) {
        int value = ToolSupport.optionalInt(args, "timeoutSeconds", defaultValue);
        if (value < 1 || value > 1800) throw new IllegalArgumentException("timeoutSeconds must be between 1 and 1800");
        return value;
    }

    private static String execute(Path directory, List<String> command, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        FutureTask<byte[]> output = new FutureTask<>(() -> process.getInputStream().readAllBytes());
        Thread.ofVirtual().name("myclaw-project-output").start(output);
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            output.cancel(true);
            throw new IllegalStateException("Command timed out after " + timeoutSeconds + " seconds: " + String.join(" ", command));
        }
        String text = ToolSupport.truncate(new String(output.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8), MAX_OUTPUT).stripTrailing();
        if (process.exitValue() != 0)
            throw new IllegalStateException("Command failed with exit code " + process.exitValue() + ": " + String.join(" ", command) + "\n" + text);
        return "Command: " + String.join(" ", command) + "\nExit code: 0\nOutput:\n" + (text.isEmpty() ? "(no output)" : text);
    }

    private static Tool tool(String name, String description, JsonNode schema, Call call) {
        ToolDefinition definition = ToolDefinition.builder(name).description(description).parameters(schema).build();
        return new Tool() {
            public ToolDefinition definition() {
                return definition;
            }

            public String call(JsonNode a, ToolContext c) throws Exception {
                return call.run(a, c);
            }
        };
    }

    private enum Operation {BUILD, TEST, LINT}

    private record Project(String type, Path directory, List<String> build, List<String> test, List<String> lint,
                           List<String> base) {
    }

    @FunctionalInterface
    private interface Call {
        String run(JsonNode args, ToolContext context) throws Exception;
    }
}
