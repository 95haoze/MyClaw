package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal persistent JSON-RPC/LSP client backed only by administrator-configured server commands. */
public final class LspQueryTool implements Tool, AutoCloseable {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESULT_CHARS = 40_000;
    private final Map<String, List<String>> commands;
    private final ConcurrentHashMap<ClientKey, Client> clients = new ConcurrentHashMap<>();

    public LspQueryTool(Map<String, List<String>> commands) {
        Map<String, List<String>> safe = new LinkedHashMap<>();
        if (commands != null) commands.forEach((language, command) -> {
            if (language != null && !language.isBlank() && command != null && !command.isEmpty() &&
                    command.stream().allMatch(item -> item != null && !item.isBlank())) {
                safe.put(language.toLowerCase(Locale.ROOT), List.copyOf(command));
            }
        });
        this.commands = Map.copyOf(safe);
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("lsp_query")
                .description("Query a configured Language Server for definition, references, hover, document symbols, or diagnostics. Server commands are backend configuration and cannot be supplied by the model.")
                .parameters(JsonSchema.object()
                        .enumOf("operation", "LSP operation", List.of("definition", "references", "hover", "symbols", "diagnostics"))
                        .string("language", "Configured language id, for example java or typescript")
                        .string("path", "Source file inside the workspace")
                        .integer("line", "One-based source line; required for definition/references/hover", false)
                        .integer("character", "One-based source column; defaults to 1", false)
                        .bool("includeDeclaration", "Include declarations in reference results", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws Exception {
        String operation = ToolSupport.requiredString(arguments, "operation");
        String language = ToolSupport.requiredString(arguments, "language").toLowerCase(Locale.ROOT);
        List<String> command = commands.get(language);
        if (command == null) {
            throw new IllegalStateException("No LSP server configured for '" + language + "'. Configured languages: " + commands.keySet());
        }
        Path file = context.resolve(ToolSupport.requiredString(arguments, "path"));
        if (!Files.isRegularFile(file)) throw new java.io.FileNotFoundException("Source file does not exist: " + file);
        ClientKey key = new ClientKey(language, context.workingDirectory().toAbsolutePath().normalize());
        Client client = clients.compute(key, (ignored, existing) -> {
            if (existing != null && existing.alive()) return existing;
            try { return new Client(command, key.workspace()); }
            catch (Exception exception) { throw new ClientStartException(exception); }
        });
        try {
            JsonNode result = client.query(operation, language, file, arguments);
            return ToolSupport.truncate(Json.pretty(result), MAX_RESULT_CHARS);
        } catch (ClientStartException exception) {
            throw exception.unwrap();
        } catch (Exception exception) {
            if (!client.alive()) clients.remove(key, client);
            throw exception;
        }
    }

    @Override public boolean enabled() { return !commands.isEmpty(); }

    @Override
    public void close() {
        clients.values().forEach(Client::close);
        clients.clear();
    }

    private record ClientKey(String language, Path workspace) {}

    private static final class Client {
        private final Process process;
        private final OutputStream output;
        private final AtomicInteger ids = new AtomicInteger();
        private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, JsonNode> diagnostics = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> documentVersions = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> diagnosticWaiters = new ConcurrentHashMap<>();

        private Client(List<String> command, Path workspace) throws Exception {
            process = new ProcessBuilder(command).directory(workspace.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            output = process.getOutputStream();
            Thread.ofVirtual().name("myclaw-lsp-reader").start(() -> readLoop(process.getInputStream()));
            ObjectNode params = Json.obj();
            params.put("processId", ProcessHandle.current().pid());
            params.put("rootUri", workspace.toUri().toString());
            params.set("capabilities", Json.obj());
            request("initialize", params);
            notify("initialized", Json.obj());
        }

        private synchronized JsonNode query(String operation, String language, Path file, JsonNode arguments) throws Exception {
            String uri = file.toUri().toString();
            ObjectNode textDocument = Json.obj().put("uri", uri);
            boolean diagnosticOperation = "diagnostics".equals(operation);
            CompletableFuture<JsonNode> diagnosticWaiter = diagnosticOperation ? new CompletableFuture<>() : null;
            if (diagnosticOperation) {
                diagnostics.remove(uri);
                diagnosticWaiters.put(uri, diagnosticWaiter);
            }
            int version = documentVersions.merge(uri, 1, Integer::sum);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (version == 1) {
                notify("textDocument/didOpen", Json.obj().set("textDocument", Json.obj()
                        .put("uri", uri).put("languageId", language).put("version", version).put("text", text)));
            } else {
                notify("textDocument/didChange", Json.obj()
                        .set("textDocument", Json.obj().put("uri", uri).put("version", version))
                        .set("contentChanges", Json.arr().add(Json.obj().put("text", text))));
            }

            if (diagnosticOperation) {
                try { return diagnosticWaiter.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS); }
                finally { diagnosticWaiters.remove(uri, diagnosticWaiter); }
            }
            ObjectNode params = Json.obj().set("textDocument", textDocument);
            if ("symbols".equals(operation)) return request("textDocument/documentSymbol", params);

            int line = ToolSupport.optionalInt(arguments, "line", 0);
            int character = ToolSupport.optionalInt(arguments, "character", 1);
            if (line < 1 || character < 1) throw new IllegalArgumentException("line and character must be one-based positive integers");
            params.set("position", Json.obj().put("line", line - 1).put("character", character - 1));
            return switch (operation) {
                case "definition" -> request("textDocument/definition", params);
                case "hover" -> request("textDocument/hover", params);
                case "references" -> {
                    params.set("context", Json.obj().put("includeDeclaration",
                            ToolSupport.optionalBool(arguments, "includeDeclaration", false)));
                    yield request("textDocument/references", params);
                }
                default -> throw new IllegalArgumentException("Unsupported LSP operation: " + operation);
            };
        }

        private JsonNode request(String method, JsonNode params) throws Exception {
            int id = ids.incrementAndGet();
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pending.put(id, future);
            ObjectNode message = Json.obj().put("jsonrpc", "2.0").put("id", id).put("method", method).set("params", params);
            send(message);
            try { return future.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS); }
            finally { pending.remove(id); }
        }

        private void notify(String method, JsonNode params) throws IOException {
            send(Json.obj().put("jsonrpc", "2.0").put("method", method).set("params", params));
        }

        private synchronized void send(JsonNode message) throws IOException {
            byte[] body = Json.write(message).getBytes(StandardCharsets.UTF_8);
            output.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(body); output.flush();
        }

        private void readLoop(java.io.InputStream source) {
            try (BufferedInputStream input = new BufferedInputStream(source)) {
                while (alive()) {
                    int length = readContentLength(input);
                    if (length < 0) break;
                    JsonNode message = Json.parse(new String(input.readNBytes(length), StandardCharsets.UTF_8));
                    JsonNode idNode = message.get("id");
                    if (idNode != null && idNode.isNumber()) {
                        CompletableFuture<JsonNode> future = pending.get(idNode.asInt());
                        if (future != null) {
                            JsonNode error = message.get("error");
                            if (error != null && !error.isNull()) future.completeExceptionally(new IllegalStateException("LSP error: " + error));
                            else future.complete(message.get("result") == null ? Json.obj() : message.get("result"));
                        }
                    } else if ("textDocument/publishDiagnostics".equals(Json.text(message, "method"))) {
                        JsonNode params = message.get("params");
                        String uri = Json.text(params, "uri");
                        JsonNode value = params == null ? Json.arr() : params.get("diagnostics");
                        if (uri != null) {
                            diagnostics.put(uri, value == null ? Json.arr() : value);
                            CompletableFuture<JsonNode> waiter = diagnosticWaiters.get(uri);
                            if (waiter != null) waiter.complete(value == null ? Json.arr() : value);
                        }
                    }
                }
            } catch (Exception exception) {
                pending.values().forEach(future -> future.completeExceptionally(exception));
            }
        }

        private static int readContentLength(BufferedInputStream input) throws IOException {
            int length = -1;
            while (true) {
                String line = readLine(input);
                if (line == null) return -1;
                if (line.isEmpty()) return length;
                int colon = line.indexOf(':');
                if (colon > 0 && "content-length".equalsIgnoreCase(line.substring(0, colon).trim()))
                    length = Integer.parseInt(line.substring(colon + 1).trim());
            }
        }

        private static String readLine(BufferedInputStream input) throws IOException {
            List<Byte> bytes = new ArrayList<>();
            int value;
            while ((value = input.read()) >= 0) {
                if (value == '\n') break;
                if (value != '\r') bytes.add((byte) value);
            }
            if (value < 0 && bytes.isEmpty()) return null;
            byte[] result = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) result[i] = bytes.get(i);
            return new String(result, StandardCharsets.US_ASCII);
        }

        private boolean alive() { return process.isAlive(); }

        private void close() {
            if (!process.isAlive()) return;
            try { request("shutdown", Json.obj()); } catch (Exception ignored) { }
            try { notify("exit", Json.obj()); } catch (Exception ignored) { }
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private static final class ClientStartException extends RuntimeException {
        private ClientStartException(Exception cause) { super(cause); }
        private Exception unwrap() { return (Exception) getCause(); }
    }
}
