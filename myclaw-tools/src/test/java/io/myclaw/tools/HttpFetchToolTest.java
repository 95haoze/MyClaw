package io.myclaw.tools;

import com.sun.net.httpserver.HttpServer;
import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HttpFetchTool} 的单元测试。
 *
 * <p>完全离线：使用 JDK 内置的 {@link HttpServer} 在本地随机端口起测试服务，不访问外网。
 */
class HttpFetchToolTest {

    private static HttpServer server;
    private static String baseUrl;

    private final HttpFetchTool tool = new HttpFetchTool(true);
    private final ToolContext context = ToolContext.defaults();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/page", exchange -> {
            String html = """
                    <html><head><title>MyClaw 测试页</title><style>body { color: red; }</style></head>
                    <body>
                    <h1>Hello MyClaw</h1>
                    <script>var x = 1 < 2 && 3 > 2;</script>
                    <p>第一段 &amp; 实体 &lt;tag&gt;</p>
                    <br>
                    <div>第二段&#65;结束</div>
                    <!-- 这是注释 -->
                    </body></html>""";
            respond(exchange, 200, "text/html; charset=utf-8", html);
        });

        server.createContext("/error", exchange ->
                respond(exchange, 500, "text/html; charset=utf-8", "<html><body>server exploded</body></html>"));

        server.createContext("/missing", exchange ->
                respond(exchange, 404, "text/plain; charset=utf-8", "not found here"));

        server.createContext("/echo", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "text/plain; charset=utf-8",
                    request.length == 0 ? "empty-body" : new String(request, StandardCharsets.UTF_8));
        });

        server.createContext("/big", exchange -> respond(exchange, 200, "text/plain; charset=utf-8", "a".repeat(30000)));

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    @DisplayName("HTML 被剥离成纯文本")
    void stripsHtmlTags() throws Exception {
        String result = tool.call(Json.obj().put("url", baseUrl + "/page"), context);

        assertThat(result).contains("Hello MyClaw");
        assertThat(result).contains("第一段 & 实体 <tag>");
        assertThat(result).contains("第二段A结束");
        assertThat(result)
                .doesNotContain("<h1>")
                .doesNotContain("</html>")
                .doesNotContain("<script>")
                .doesNotContain("var x")
                .doesNotContain("color: red")
                .doesNotContain("这是注释");
    }

    @Test
    @DisplayName("状态码 >= 400 时抛异常并带上状态码与响应体片段")
    void failsOnErrorStatus() {
        assertThatThrownBy(() -> tool.call(Json.obj().put("url", baseUrl + "/error"), context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("server exploded");

        assertThatThrownBy(() -> tool.call(Json.obj().put("url", baseUrl + "/missing"), context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("not found here");
    }

    @Test
    @DisplayName("非 http/https 协议被拒绝")
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> tool.call(Json.obj().put("url", "file:///C:/Windows/win.ini"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http/https");
        assertThatThrownBy(() -> tool.call(Json.obj().put("url", "ftp://example.com/a.txt"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http/https");
        assertThatThrownBy(() -> tool.call(Json.obj().put("url", "example.com/page"), context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("不支持的 HTTP 方法被拒绝")
    void rejectsUnsupportedMethod() {
        assertThatThrownBy(() -> tool.call(Json.obj().put("url", baseUrl + "/page").put("method", "DELETE"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELETE");
    }

    @Test
    @DisplayName("POST 会发送请求体")
    void sendsPostBody() throws Exception {
        String result = tool.call(
                Json.obj().put("url", baseUrl + "/echo").put("method", "POST").put("body", "ping-myclaw"), context);

        assertThat(result).contains("ping-myclaw");
    }

    @Test
    @DisplayName("超长响应体被截断并注明")
    void truncatesLongBody() throws Exception {
        String result = tool.call(Json.obj().put("url", baseUrl + "/big"), context);

        assertThat(result).contains("已截断").hasSizeLessThan(21000);
    }

    @Test
    @DisplayName("缺少 url 参数时报错")
    void requiresUrl() {
        assertThatThrownBy(() -> tool.call(Json.obj(), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }
}
