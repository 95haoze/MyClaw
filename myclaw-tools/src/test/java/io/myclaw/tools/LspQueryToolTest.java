package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LspQueryToolTest {
    @TempDir Path workspace;

    @Test
    void queriesConfiguredServerAndReceivesDiagnostics() throws Exception {
        Path source = workspace.resolve("Demo.java");
        Files.writeString(source, "class Demo {}\n");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        LspQueryTool tool = new LspQueryTool(Map.of("java", List.of(
                java, "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), MockServer.class.getName()
        )));
        ToolContext context = ToolContext.of(workspace);

        String symbols = tool.call(Json.obj().put("operation", "symbols").put("language", "java")
                .put("path", "Demo.java"), context);
        assertThat(symbols).contains("Demo");

        String diagnostics = tool.call(Json.obj().put("operation", "diagnostics").put("language", "java")
                .put("path", "Demo.java"), context);
        assertThat(diagnostics).contains("mock diagnostic");
        tool.close();
    }

    @Test
    void rejectsLanguagesWithoutBackendConfiguration() throws Exception {
        Files.writeString(workspace.resolve("a.ts"), "const a = 1");
        LspQueryTool tool = new LspQueryTool(Map.of());
        assertThat(tool.enabled()).isFalse();
        assertThatThrownBy(() -> tool.call(Json.obj().put("operation", "symbols").put("language", "typescript")
                .put("path", "a.ts"), ToolContext.of(workspace)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No LSP server configured");
    }

    public static final class MockServer {
        public static void main(String[] args) throws Exception {
            BufferedInputStream input = new BufferedInputStream(System.in);
            while (true) {
                int length = contentLength(input);
                if (length < 0) return;
                JsonNode message = Json.parse(new String(input.readNBytes(length), StandardCharsets.UTF_8));
                String method = Json.text(message, "method", "");
                JsonNode id = message.get("id");
                if (id != null && id.isNumber()) {
                    JsonNode result;
                    if ("initialize".equals(method)) result = Json.obj().set("capabilities", Json.obj());
                    else if ("textDocument/documentSymbol".equals(method)) result = Json.arr().add(
                            Json.obj().put("name", "Demo").put("kind", 5)
                                    .set("range", Json.obj().set("start", Json.obj().put("line", 0).put("character", 0))
                                            .set("end", Json.obj().put("line", 0).put("character", 10))));
                    else result = Json.arr();
                    send(Json.obj().put("jsonrpc", "2.0").put("id", id.asInt()).set("result", result));
                }
                if ("textDocument/didOpen".equals(method) || "textDocument/didChange".equals(method)) {
                    String uri = Json.text(message.path("params").path("textDocument"), "uri");
                    send(Json.obj().put("jsonrpc", "2.0").put("method", "textDocument/publishDiagnostics")
                            .set("params", Json.obj().put("uri", uri).set("diagnostics", Json.arr().add(
                                    Json.obj().put("message", "mock diagnostic").put("severity", 2)))));
                }
            }
        }

        private static void send(JsonNode message) throws Exception {
            byte[] body = Json.write(message).getBytes(StandardCharsets.UTF_8);
            System.out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            System.out.write(body); System.out.flush();
        }

        private static int contentLength(BufferedInputStream input) throws Exception {
            int length = -1;
            while (true) {
                String line = line(input);
                if (line == null) return -1;
                if (line.isEmpty()) return length;
                if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            }
        }

        private static String line(BufferedInputStream input) throws Exception {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int value;
            while ((value = input.read()) >= 0) { if (value == '\n') break; if (value != '\r') output.write(value); }
            if (value < 0 && output.size() == 0) return null;
            return output.toString(StandardCharsets.US_ASCII);
        }
    }
}
