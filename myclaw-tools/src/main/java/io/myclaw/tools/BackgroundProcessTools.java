package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Long-running process tools sharing one in-memory process registry.
 */
public final class BackgroundProcessTools implements AutoCloseable {
    private static final int MAX_BUFFER_CHARS = 200_000;
    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();
    private final List<String> allowlistPrefixes;

    public BackgroundProcessTools(List<String> allowlistPrefixes) {
        this.allowlistPrefixes = allowlistPrefixes == null ? List.of() : allowlistPrefixes;
    }

    public List<Tool> tools() {
        return List.of(start(), readOutput(), stop(), list());
    }

    public Tool start() {
        return tool("start_process", "Start a long-running command in the workspace and return a managed process id.",
                JsonSchema.object().string("command", "Command to start")
                        .string("name", "Optional descriptive name", false).build(), this::startProcess);
    }

    public Tool readOutput() {
        return tool("read_process_output", "Read new combined stdout/stderr from a managed process.",
                JsonSchema.object().string("processId", "Managed process id")
                        .integer("maxChars", "Maximum returned characters, 1-50000; defaults to 20000", false).build(),
                this::readProcessOutput);
    }

    public Tool stop() {
        return tool("stop_process", "Stop a managed process and its descendants.",
                JsonSchema.object().string("processId", "Managed process id")
                        .bool("force", "Force termination immediately", false).build(), this::stopProcess);
    }

    public Tool list() {
        return tool("list_processes", "List processes started by start_process and their current state.",
                JsonSchema.object().bool("includeExited", "Include exited processes; defaults to true", false).build(),
                this::listProcesses);
    }

    private String startProcess(JsonNode args, ToolContext context) throws IOException {
        String command = ToolSupport.requiredString(args, "command").trim();
        ShellTool.checkCommandSafety(command, allowlistPrefixes);
        ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
        builder.directory(context.workingDirectory().toFile()).redirectErrorStream(true);
        Process process = builder.start();
        String id = UUID.randomUUID().toString().substring(0, 8);
        ManagedProcess managed = new ManagedProcess(id, ToolSupport.optionalString(args, "name", command), command, process);
        processes.put(id, managed);
        managed.reader = Thread.ofVirtual().name("myclaw-process-" + id).start(() -> managed.capture(process.getInputStream()));
        return "Process id: " + id + "\nPID: " + process.pid() + "\nState: RUNNING\nCommand: " + command;
    }

    private String readProcessOutput(JsonNode args, ToolContext context) {
        ManagedProcess process = required(args);
        int maxChars = ToolSupport.optionalInt(args, "maxChars", 20_000);
        if (maxChars < 1 || maxChars > 50_000)
            throw new IllegalArgumentException("maxChars must be between 1 and 50000");
        String output = process.readNew(maxChars);
        return describe(process) + "\nNew output:\n" + (output.isEmpty() ? "(no new output)" : output);
    }

    private String stopProcess(JsonNode args, ToolContext context) throws InterruptedException {
        ManagedProcess managed = required(args);
        boolean force = ToolSupport.optionalBool(args, "force", false);
        destroyTree(managed.process, force);
        if (!force && !managed.process.waitFor(3, TimeUnit.SECONDS)) destroyTree(managed.process, true);
        managed.process.waitFor(3, TimeUnit.SECONDS);
        return describe(managed);
    }

    private String listProcesses(JsonNode args, ToolContext context) {
        boolean includeExited = ToolSupport.optionalBool(args, "includeExited", true);
        StringBuilder result = new StringBuilder();
        processes.values().stream().sorted(Comparator.comparing(p -> p.startedAt)).forEach(process -> {
            if (includeExited || process.process.isAlive()) result.append(describe(process)).append('\n');
        });
        return result.isEmpty() ? "No managed processes." : result.toString().stripTrailing();
    }

    private ManagedProcess required(JsonNode args) {
        String id = ToolSupport.requiredString(args, "processId");
        ManagedProcess process = processes.get(id);
        if (process == null) throw new IllegalArgumentException("Unknown processId: " + id);
        return process;
    }

    private static String describe(ManagedProcess p) {
        String state = p.process.isAlive() ? "RUNNING" : "EXITED(" + p.process.exitValue() + ")";
        return "Process " + p.id + " [" + p.name + "] PID=" + p.process.pid() + " state=" + state + " started=" + p.startedAt;
    }

    private static void destroyTree(Process process, boolean force) {
        process.descendants().forEach(handle -> {
            if (force) handle.destroyForcibly();
            else handle.destroy();
        });
        if (force) process.destroyForcibly();
        else process.destroy();
    }

    private static List<String> shellCommand(String command) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? List.of("cmd.exe", "/d", "/c", command) : List.of("/bin/sh", "-c", command);
    }

    private static Tool tool(String name, String description, JsonNode schema, Call call) {
        ToolDefinition definition = ToolDefinition.builder(name).description(description).parameters(schema).build();
        return new Tool() {
            public ToolDefinition definition() {
                return definition;
            }

            public String call(JsonNode args, ToolContext context) throws Exception {
                return call.run(args, context);
            }
        };
    }

    @Override
    public void close() {
        processes.values().stream().filter(p -> p.process.isAlive()).forEach(p -> destroyTree(p.process, true));
    }

    private static final class ManagedProcess {
        final String id, name, command;
        final Process process;
        final Instant startedAt = Instant.now();
        final StringBuilder output = new StringBuilder();
        int readPosition;
        Thread reader;

        ManagedProcess(String id, String name, String command, Process process) {
            this.id = id;
            this.name = name;
            this.command = command;
            this.process = process;
        }

        void capture(InputStream input) {
            try (input) {
                byte[] bytes = new byte[4096];
                int count;
                while ((count = input.read(bytes)) >= 0) append(new String(bytes, 0, count, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }

        synchronized void append(String text) {
            output.append(text);
            if (output.length() > MAX_BUFFER_CHARS) {
                int remove = output.length() - MAX_BUFFER_CHARS;
                output.delete(0, remove);
                readPosition = Math.max(0, readPosition - remove);
            }
        }

        synchronized String readNew(int maxChars) {
            int end = Math.min(output.length(), readPosition + maxChars);
            String value = output.substring(readPosition, end);
            readPosition = end;
            return value;
        }
    }

    @FunctionalInterface
    private interface Call {
        String run(JsonNode args, ToolContext context) throws Exception;
    }
}
