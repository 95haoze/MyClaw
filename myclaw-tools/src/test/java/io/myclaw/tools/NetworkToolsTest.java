package io.myclaw.tools;

import com.sun.net.httpserver.HttpServer;
import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkToolsTest {
    @TempDir Path workspace;
    HttpServer server;

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test
    void blocksPrivateAndLoopbackAddressesByDefault() {
        assertThatThrownBy(() -> new NetworkTools.SecureHttp().send(
                URI.create("http://127.0.0.1/private"), "GET", null, Map.of(), 1000))
                .isInstanceOf(SecurityException.class).hasMessageContaining("Private or local");
    }

    @Test
    void boundedClientFollowsRedirectAndInjectsConfiguredHeader() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/result"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/result", exchange -> {
            byte[] body = exchange.getRequestHeaders().getFirst("Authorization").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/start");

        var response = new NetworkTools.SecureHttp(true).send(uri, "GET", null,
                Map.of("Authorization", "Bearer server-secret"), 1000);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("Bearer server-secret");
    }

    @Test
    void enforcesResponseSizeLimit() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            byte[] body = "x".repeat(200).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/large");
        assertThatThrownBy(() -> new NetworkTools.SecureHttp(true).send(uri, "GET", null, Map.of(), 100))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exceeds");
    }

    @Test
    void credentialsAreNamedAndApiPathsCannotEscapeOrigin() {
        var profile = new NetworkTools.CredentialProfile("https://api.example.com/v1/", Map.of("Authorization", "secret"), false);
        var tool = NetworkTools.apiRequest(Map.of("main", profile));
        assertThat(tool.definition().name()).isEqualTo("api_request");
        assertThatThrownBy(() -> tool.call(Json.obj().put("profile", "missing").put("path", "items"), ToolContext.of(workspace)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown credential profile");
        assertThatThrownBy(() -> tool.call(Json.obj().put("profile", "main").put("path", "https://evil.example/items"), ToolContext.of(workspace)))
                .isInstanceOf(SecurityException.class).hasMessageContaining("origin");
    }

    @Test
    void webSearchReturnsNormalizedResultsAndKeepsCredentialsServerSide() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("q=java+records", "page=2");
            assertThat(exchange.getRequestHeaders().getFirst("X-Subscription-Token")).isEqualTo("server-secret");
            byte[] body = """
                    {"web":{"results":[
                      {"title":"Java Records","url":"https://example.com/records","description":"Concise data carriers"},
                      {"title":"Record classes","url":"https://example.com/classes","description":"Language reference"}
                    ]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var credentials = Map.of("search", new NetworkTools.CredentialProfile(base + "/", Map.of(
                "X-Subscription-Token", "server-secret"), true));
        var providers = Map.of("brave", new NetworkTools.SearchProvider(
                base + "/search?q={query}&page={page}", "search", "brave"));

        String result = NetworkTools.webSearch(providers, credentials).call(
                Json.obj().put("query", "java records").put("page", 2), ToolContext.of(workspace));

        assertThat(result).contains("1. Java Records", "URL: https://example.com/records", "Concise data carriers")
                .doesNotContain("server-secret", "{\"web\"");
    }}
