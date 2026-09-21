package io.myclaw.tools;

import com.microsoft.playwright.*;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 基于 Playwright 的受限浏览器工具集。一个实例持有一个浏览器上下文和一个页面，
 * 七个工具共享该页面，因此可以连续执行 open/click/type/text 等操作。
 * 下载和文件上传均不向模型暴露；下载会被取消，文件选择事件会被拒绝。
 */
public final class BrowserAutomationTools implements AutoCloseable {
    private static final int MAX_TEXT = 20_000;
    private static final int MAX_LOGS = 200;
    private final List<Pattern> allowedHosts;
    private final boolean allowPrivateNetwork;
    private final Object lock = new Object();
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;
    private final Deque<String> console = new ArrayDeque<>();
    private final Deque<String> network = new ArrayDeque<>();
    private volatile boolean closed;

    public BrowserAutomationTools(List<String> allowedHosts, boolean allowPrivateNetwork, boolean headless) {
        this.allowedHosts = compileHosts(allowedHosts);
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
        this.browser = playwright.chromium().launch(options);
        this.context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(false));
        //this.context.onDownload(Download::cancel);
        this.page = context.newPage();
        this.page.onConsoleMessage(this::recordConsole);
        this.page.onRequest(request -> record(network, "REQUEST " + request.method() + " " + request.url()));
        this.page.onResponse(response -> record(network, "RESPONSE " + response.status() + " " + response.url()));
        this.page.onFileChooser(fileChooser -> {
            throw new SecurityException("浏览器文件上传已禁用");
        });
        this.page.route("**/*", route -> {
            checkUrl(route.request().url());
            route.resume();
        });
    }

    public List<Tool> tools() {
        return List.of(new Open(), new Click(), new Type(), new Screenshot(), new Text(), new Console(), new Network());
    }

    private void checkUrl(String raw) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("非法 URL: " + raw, e);
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SecurityException("只允许 http/https URL: " + raw);
        }
        String host = uri.getHost();
        if (host == null || (!allowedHosts.isEmpty() && allowedHosts.stream().noneMatch(p -> p.matcher(host).matches()))) {
            throw new SecurityException("域名不在浏览器白名单中: " + host);
        }
        if (!allowPrivateNetwork && isPrivate(host)) {
            throw new SecurityException("浏览器禁止访问私有网络地址: " + host);
        }
    }

    private static boolean isPrivate(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || "localhost".equalsIgnoreCase(host);
        } catch (IOException e) {
            return true;
        }
    }

    private static List<Pattern> compileHosts(List<String> hosts) {
        List<Pattern> result = new ArrayList<>();
        if (hosts != null) for (String host : hosts)
            if (host != null && !host.isBlank()) {
                String value = host.trim().toLowerCase(Locale.ROOT);
                String regex = value.startsWith("*.") ? "(?:[^.]+\\.)*" + Pattern.quote(value.substring(2)) : Pattern.quote(value);
                result.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            }
        return List.copyOf(result);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("浏览器已关闭");
    }

    private static String required(JsonNode args, String name) {
        return ToolSupport.requiredString(args, name).trim();
    }

    private static String optional(JsonNode args, String name, String fallback) {
        return ToolSupport.optionalString(args, name, fallback);
    }

    private static String truncate(String value) {
        return ToolSupport.truncate(value == null ? "" : value, MAX_TEXT);
    }

    private void recordConsole(ConsoleMessage message) {
        record(console, message.type().toUpperCase(Locale.ROOT) + ": " + message.text());
    }

    private static void record(Deque<String> target, String value) {
        synchronized (target) {
            if (target.size() >= MAX_LOGS) target.removeFirst();
            target.addLast(value);
        }
    }

    private static String logs(Deque<String> target) {
        synchronized (target) {
            return truncate(String.join("\n", target));
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (!closed) {
                closed = true;
                context.close();
                browser.close();
                playwright.close();
            }
        }
    }

    private abstract class Base implements Tool {
        protected <T> T sync(java.util.concurrent.Callable<T> action) throws Exception {
            synchronized (lock) {
                ensureOpen();
                return action.call();
            }
        }
    }

    private final class Open extends Base {
        public ToolDefinition definition() {
            return ToolDefinition.builder("browser_open").description("在受限浏览器中打开网页并等待加载完成").parameters(JsonSchema.object().string("url", "http/https URL").build()).build();
        }

        public String call(JsonNode a, ToolContext c) throws Exception {
            return sync(() -> {
                String url = required(a, "url");
                checkUrl(url);
                page.navigate(url);
                return "已打开: " + page.url() + "\n标题: " + page.title();
            });
        }
    }

    private final class Click extends Base {
        public ToolDefinition definition() {
            return ToolDefinition.builder("browser_click").description("按 CSS 选择器点击当前页面元素").parameters(JsonSchema.object().string("selector", "CSS 选择器").build()).build();
        }

        public String call(JsonNode a, ToolContext c) throws Exception {
            return sync(() -> {
                page.locator(required(a, "selector")).click();
                return "已点击: " + required(a, "selector");
            });
        }
    }

    private final class Type extends Base {
        public ToolDefinition definition() {
            return ToolDefinition.builder("browser_type").description("向当前页面元素输入文本，不支持文件上传").parameters(JsonSchema.object().string("selector", "CSS 选择器").string("text", "要输入的文本").build()).build();
        }

        public String call(JsonNode a, ToolContext c) throws Exception {
            return sync(() -> {
                String selector = required(a, "selector");
                page.locator(selector).fill(required(a, "text"));
                return "已输入文本: " + selector;
            });
        }
    }

    private final class Screenshot extends Base {
        public ToolDefinition definition() {
            return ToolDefinition.builder("browser_screenshot").description("将当前页面截图保存到工作目录内").parameters(JsonSchema.object().string("path", "工作目录内的 PNG 文件路径").build()).build();
        }

        public String call(JsonNode a, ToolContext c) throws Exception {
            return sync(() -> {
                Path path = c.resolve(required(a, "path"));
                if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    throw new IllegalArgumentException("截图路径必须以 .png 结尾");
                Files.createDirectories(path.getParent());
                page.screenshot(new Page.ScreenshotOptions().setPath(path));
                return "截图已保存: " + path;
            });
        }
    }

    private final class Text extends Base {
        public ToolDefinition definition() {
            return ToolDefinition.builder("browser_text").description("读取当前页面或指定元素的可见文本").parameters(JsonSchema.object().string("selector", "可选 CSS 选择器，不填则读取整页", false).build()).build();
        }

        public String call(JsonNode a, ToolContext c) throws Exception {
            return sync(() -> truncate(page.locator(optional(a, "selector", "body")).innerText()));
        }
    }

    private final class Console extends Base {
        public ToolDefinition definition() {
            return ToolDefinition.builder("browser_console").description("读取浏览器控制台日志").parameters(JsonSchema.object().build()).build();
        }

        public String call(JsonNode a, ToolContext c) {
            ensureOpen();
            return logs(console);
        }
    }

    private final class Network extends Base {
        public ToolDefinition definition() {
            return ToolDefinition.builder("browser_network").description("读取当前浏览器会话捕获的请求和响应摘要").parameters(JsonSchema.object().build()).build();
        }

        public String call(JsonNode a, ToolContext c) {
            ensureOpen();
            return logs(network);
        }
    }
}
