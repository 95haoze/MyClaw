package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 抓取工具 —— 让模型读取网页或调用 HTTP 接口。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code url}（string，必填）：目标地址，仅支持 {@code http} / {@code https}</li>
 *   <li>{@code method}（string，可选）：{@code GET} 或 {@code POST}，默认 {@code GET}</li>
 *   <li>{@code body}（string，可选）：POST 请求体</li>
 * </ul>
 *
 * <p>返回：响应体转换为纯文本后的内容（已去除脚本/样式/标签、解码 HTML 实体、压缩空白），
 * 超过 20000 字符会截断并注明。
 *
 * <p>其它：连接与读取超时均为 20 秒，自动跟随重定向，{@code User-Agent} 固定为 {@code MyClaw/1.0}；
 * HTTP 状态码 &gt;= 400 时抛异常，异常信息包含状态码与响应体前 500 个字符。
 */
public class HttpFetchTool implements Tool {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_BODY_CHARS = 20000;
    private static final int MAX_ERROR_BODY_CHARS = 500;
    private static final String USER_AGENT = "MyClaw/1.0";
    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST");

    private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b[^>]*>.*?</script\\s*>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style\\b[^>]*>.*?</style\\s*>");
    private static final Pattern COMMENT = Pattern.compile("(?is)<!--.*?-->");
    private static final Pattern LINE_BREAK_TAG = Pattern.compile("(?is)<br\\s*/?>");
    private static final Pattern BLOCK_END_TAG = Pattern.compile(
            "(?is)</(p|div|li|tr|h[1-6]|section|article|header|footer|ul|ol|table|blockquote|pre)\\s*>");
    private static final Pattern ANY_TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);");
    private static final Pattern INLINE_BLANK = Pattern.compile("[\\t\\x0B\\f ]+");

    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
            Map.entry("copy", "©"), Map.entry("reg", "®"), Map.entry("trade", "™"),
            Map.entry("hellip", "…"), Map.entry("mdash", "—"), Map.entry("ndash", "–"),
            Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"),
            Map.entry("middot", "·"), Map.entry("times", "×"), Map.entry("divide", "÷"), Map.entry("deg", "°"),
            Map.entry("euro", "€"), Map.entry("pound", "£"), Map.entry("yen", "¥"), Map.entry("sect", "§"),
            Map.entry("bull", "•"), Map.entry("laquo", "«"), Map.entry("raquo", "»"), Map.entry("permil", "‰"));

    private final NetworkTools.SecureHttp client;

    public HttpFetchTool() { this(false); }

    HttpFetchTool(boolean allowPrivateNetwork) {
        this.client = new NetworkTools.SecureHttp(allowPrivateNetwork);
    }
    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder("http_fetch")
                .description("""
                        IMPORTANT: This is not a search engine. Use it only for a known explicit URL supplied by the user or returned by web_search.
                        If web_search is unavailable, explain that web search is not configured; never invent a search URL.
                        发起 HTTP 请求并返回响应正文的纯文本（HTML 会被去除标签、脚本与样式，最长 20000 字符）。
                        仅支持 http/https，超时 20 秒，自动跟随重定向。用于查阅网页或调用 REST 接口。""")
                .parameters(JsonSchema.object()
                        .string("url", "完整地址，例如 https://example.com/page")
                        .enumOf("method", "HTTP 方法，默认 GET", ALLOWED_METHODS, false)
                        .string("body", "POST 请求体，method=POST 时使用", false)
                        .build())
                .build();
    }

    @Override
    public String call(JsonNode arguments, ToolContext context) throws IOException, InterruptedException {
        String url = ToolSupport.requiredString(arguments, "url").trim();
        String method = ToolSupport.optionalString(arguments, "method", "GET").trim().toUpperCase(Locale.ROOT);
        String body = ToolSupport.optionalString(arguments, "body", null);

        if (!ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException("不支持的 HTTP 方法 `" + method + "`，仅支持: " + ALLOWED_METHODS);
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法的 URL `" + url + "`：" + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "只允许 http/https 协议，实际为: " + (scheme == null ? "（缺少协议头）" : scheme));
        }

        Map<String, String> headers = Map.of(
                "Accept", "text/html,application/json,text/plain,*/*"
        );
        NetworkTools.SecureHttp.Response response;
        try {
            response = client.send(uri, method, "POST".equals(method) ? (body == null ? "" : body) : null,
                    headers, 2 * 1024 * 1024);
        } catch (IOException | InterruptedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("HTTP request failed: " + exception.getMessage(), exception);
        }
        String raw = decode(response.body(), response.headers().firstValue("content-type"));
        int status = response.status();
        if (status >= 400) {
            throw new IllegalStateException("HTTP 请求失败: 状态码 " + status + "，URL: " + uri
                    + "，响应体前 " + MAX_ERROR_BODY_CHARS + " 个字符:\n"
                    + abbreviate(raw, MAX_ERROR_BODY_CHARS));
        }
        return ToolSupport.truncate(htmlToText(raw), MAX_BODY_CHARS);
    }

    /** 按响应头中的 charset 解码响应体，缺省使用 UTF-8。 */
    private static String decode(byte[] body, Optional<String> contentType) {
        Charset charset = StandardCharsets.UTF_8;
        if (contentType.isPresent()) {
            String header = contentType.get().toLowerCase(Locale.ROOT);
            int index = header.indexOf("charset=");
            if (index >= 0) {
                String name = header.substring(index + "charset=".length()).trim();
                int semicolon = name.indexOf(';');
                if (semicolon >= 0) name = name.substring(0, semicolon);
                name = name.replace("\"", "").replace("'", "").trim();
                try { charset = Charset.forName(name); } catch (RuntimeException ignored) { charset = StandardCharsets.UTF_8; }
            }
        }
        return new String(body, charset);
    }
    /**
     * 把 HTML 粗略转换为纯文本：去脚本/样式/注释、把换行类标签变成换行、剥离所有标签、
     * 解码常见 HTML 实体、压缩连续空白。
     *
     * @param html 原始响应体
     * @return 纯文本
     */
    static String htmlToText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = SCRIPT.matcher(html).replaceAll(" ");
        text = STYLE.matcher(text).replaceAll(" ");
        text = COMMENT.matcher(text).replaceAll(" ");
        text = LINE_BREAK_TAG.matcher(text).replaceAll("\n");
        text = BLOCK_END_TAG.matcher(text).replaceAll("\n");
        text = ANY_TAG.matcher(text).replaceAll("");
        text = decodeEntities(text);
        return collapseWhitespace(text);
    }

    /** 解码命名实体与数字实体（单遍扫描，因此 {@code &amp;lt;} 会正确得到 {@code &lt;}）。 */
    static String decodeEntities(String text) {
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = matcher.group();
            if (name.startsWith("#")) {
                try {
                    int codePoint = name.startsWith("#x") || name.startsWith("#X")
                            ? Integer.parseInt(name.substring(2), 16)
                            : Integer.parseInt(name.substring(1));
                    replacement = new String(Character.toChars(codePoint));
                } catch (RuntimeException ignored) {
                    replacement = matcher.group();
                }
            } else {
                String named = ENTITIES.get(name.toLowerCase(Locale.ROOT));
                if (named != null) {
                    replacement = named;
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 统一换行、压缩行内空白、把连续空行压成一个空行。 */
    static String collapseWhitespace(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = INLINE_BLANK.matcher(normalized).replaceAll(" ");
        StringBuilder sb = new StringBuilder(normalized.length());
        boolean previousBlank = true;
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                if (previousBlank) {
                    continue;
                }
                previousBlank = true;
            } else {
                previousBlank = false;
            }
            sb.append(trimmed).append('\n');
        }
        return sb.toString().strip();
    }

    private static String abbreviate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }
}
