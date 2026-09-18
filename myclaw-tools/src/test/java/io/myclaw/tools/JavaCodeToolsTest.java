package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCodeToolsTest {
    @TempDir Path workspace;
    ToolContext context;

    @BeforeEach
    void createSources() throws Exception {
        context = ToolContext.of(workspace);
        Files.writeString(workspace.resolve("OrderService.java"), """
                package demo;
                public class OrderService {
                    private final OrderRepository repository = new OrderRepository();
                    public String findOrder(long id) {
                        return repository.findOrder(id);
                    }
                }
                class OrderRepository {
                    String findOrder(long id) { return String.valueOf(id); }
                }
                """);
    }

    @Test
    void findsDeclarationsByAstKind() throws Exception {
        String type = JavaCodeTools.findSymbol().call(Json.obj().put("query", "OrderService"), context);
        String methods = JavaCodeTools.findSymbol().call(
                Json.obj().put("query", "findOrder").put("kind", "method"), context);
        assertThat(type).contains("OrderService.java:2", "type OrderService");
        assertThat(methods).contains("method findOrder");
    }

    @Test
    void findsReferencesAndCanIncludeDeclarations() throws Exception {
        String references = JavaCodeTools.findReferences().call(
                Json.obj().put("symbol", "findOrder").put("includeDeclarations", true), context);
        assertThat(references).contains("reference findOrder", "declaration findOrder");
    }

    @Test
    void returnsNestedOutline() throws Exception {
        String outline = JavaCodeTools.outline().call(Json.obj().put("path", "OrderService.java"), context);
        assertThat(outline).contains("class OrderService", "method findOrder(long id)", "variable OrderRepository repository");
    }

    @Test
    void reportsSyntaxDiagnostics() throws Exception {
        Files.writeString(workspace.resolve("Broken.java"), "class Broken { void run( { } ");
        String diagnostics = JavaCodeTools.diagnostics().call(Json.obj().put("path", "Broken.java"), context);
        assertThat(diagnostics).contains("Broken.java:", "[error]");
    }
}
