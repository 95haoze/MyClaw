package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Secure network tools configured entirely by the backend.
 */
public final class NetworkTools {
    public record CredentialProfile(String baseUrl, Map<String, String> headers, boolean allowPrivateNetwork) {
        public CredentialProfile {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }

    public record SearchProvider(String urlTemplate, String credentialProfile, String responseFormat, String method) {
        public SearchProvider {
            responseFormat = responseFormat == null || responseFormat.isBlank() ? "auto" : responseFormat;
            method = method == null || method.isBlank() ? "GET" : method.toUpperCase(Locale.ROOT);
            if (!List.of("GET", "POST").contains(method)) {
                throw new IllegalArgumentException("Search provider method must be GET or POST");
            }
        }

        public SearchProvider(String urlTemplate, String credentialProfile, String responseFormat) {
            this(urlTemplate, credentialProfile, responseFormat, "GET");
        }

        public SearchProvider(String urlTemplate, String credentialProfile) {
            this(urlTemplate, credentialProfile, "auto", "GET");
        }
    }

    private static final SecureHttp HTTP = new SecureHttp();

    private NetworkTools() {
    }

    public static Tool webSearch(Map<String, SearchProvider> providers, Map<String, CredentialProfile> credentials) {

        Map<String, SearchProvider> safeProviders = providers == null ? Map.of() : Map.copyOf(providers);
        return tool("web_search", "Search the web through a backend-configured provider. Query credentials are never supplied by or exposed to the model.",
                JsonSchema.object()
                        .string("query", "Search query")
                        .string("provider", "Configured provider name; defaults to the first provider", false)
                        .integer("page", "One-based result page, 1-20", false)
                        .build(),
                (args, context) -> {
                    if (safeProviders.isEmpty()) {
                        throw new IllegalStateException("No web search provider is configured");
                    }
                    String providerName = ToolSupport.optionalString(args, "provider", safeProviders.keySet().iterator().next());
                    SearchProvider provider = safeProviders.get(providerName);
                    if (provider == null) {
                        throw new IllegalArgumentException("Unknown search provider '" + providerName + "'. Available: " + safeProviders.keySet());
                    }
                    int page = ToolSupport.optionalInt(args, "page", 1);
                    if (page < 1 || page > 20) {
                        throw new IllegalArgumentException("page must be between 1 and 20");
                    }
                    String rawQuery = ToolSupport.requiredString(args, "query");
                    String query = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8);
                    String url = provider.urlTemplate().replace("{query}", query).replace("{page}", String.valueOf(page));
                    CredentialProfile profile = profile(credentials, provider.credentialProfile(), false);
                    String requestBody = null;
                    if ("POST".equals(provider.method())) {
                        requestBody = Json.write(Json.obj()
                                .put("query", rawQuery)
                                .put("search_depth", "basic")
                                .put("max_results", 10)
                                .put("topic", "general")
                                .put("include_answer", false)
                                .put("include_raw_content", false));
                    }
                    SecureHttp.Response response = HTTP.send(URI.create(url), provider.method(), requestBody, headers(profile),
                            2_000_000, profile != null && profile.allowPrivateNetwork());
                    if (response.status() >= 400) throw new IllegalStateException("Search provider returned HTTP " + response.status());
                    return formatSearchResults(response.body(), provider.responseFormat());
                });
    }

    static String formatSearchResults(byte[] body, String configuredFormat) {
        String raw = new String(body, StandardCharsets.UTF_8);
        String format = configuredFormat == null ? "auto" : configuredFormat.toLowerCase(Locale.ROOT);
        if ("text".equals(format)) return ToolSupport.truncate(HttpFetchTool.htmlToText(raw), 20_000);
        try {
            JsonNode root = Json.parse(raw);
            JsonNode results = searchResultArray(root, format);
            if (results == null || !results.isArray()) return ToolSupport.truncate(Json.pretty(root), 20_000);
            StringBuilder output = new StringBuilder();
            int index = 0;
            for (JsonNode item : results) {
                if (++index > 10) break;
                String title = firstText(item, "title", "name");
                String url = firstText(item, "url", "link", "href");
                String snippet = firstText(item, "description", "snippet", "content", "body");
                output.append(index).append(". ").append(title.isBlank() ? "Untitled result" : title).append('\n');
                if (!url.isBlank()) output.append("   URL: ").append(url).append('\n');
                if (!snippet.isBlank()) output.append("   ").append(snippet.replaceAll("\\s+", " ").trim()).append('\n');
            }
            return output.isEmpty() ? "No search results found." : ToolSupport.truncate(output.toString().trim(), 20_000);
        } catch (RuntimeException exception) {
            if (!"auto".equals(format)) throw new IllegalStateException("Invalid " + format + " search response", exception);
            return ToolSupport.truncate(HttpFetchTool.htmlToText(raw), 20_000);
        }
    }

    private static JsonNode searchResultArray(JsonNode root, String format) {
        if ("brave".equals(format) || "auto".equals(format)) {
            JsonNode web = root == null ? null : root.get("web");
            if (web != null && web.get("results") != null) return web.get("results");
        }
        if (("serper".equals(format) || "auto".equals(format)) && root != null && root.get("organic") != null) return root.get("organic");
        if (("tavily".equals(format) || "auto".equals(format)) && root != null && root.get("results") != null) return root.get("results");
        return null;
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null) return "";
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isString() && !value.asString().isBlank()) return value.asString();
        }
        return "";
    }

    public static Tool apiRequest(Map<String, CredentialProfile> credentials) {
        return tool("api_request", "Call an HTTP API through a named backend credential profile. Tokens, cookies, and authentication headers never enter tool arguments or results.",
                JsonSchema.object().string("profile", "Backend credential profile name")
                        .string("path", "Relative API path")
                        .enumOf("method", "HTTP method", List.of("GET", "POST", "PUT", "PATCH", "DELETE"), false)
                        .string("body", "Optional request body", false).build(),
                (args, context) -> {
                    CredentialProfile profile = profile(credentials, ToolSupport.requiredString(args, "profile"), true);
                    URI uri = resolveProfileUri(profile, ToolSupport.requiredString(args, "path"));
                    String method = ToolSupport.optionalString(args, "method", "GET").toUpperCase(Locale.ROOT);
                    if (!List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                        throw new IllegalArgumentException("Unsupported method: " + method);
                    }
                    String body = ToolSupport.optionalString(args, "body", "");
                    return responseText(HTTP.send(uri, method, body, headers(profile), 2_000_000, profile.allowPrivateNetwork()));
                });
    }

    public static Tool downloadFile(Map<String, CredentialProfile> credentials) {
        return tool("download_file", "Download a remote file into the workspace with redirect, SSRF, size, and overwrite protection.",
                JsonSchema.object().string("url", "Public HTTP/HTTPS URL", false)
                        .string("profile", "Optional backend credential profile", false)
                        .string("path", "Relative API path when profile is used", false)
                        .string("destination", "Destination path inside the workspace")
                        .bool("overwrite", "Allow replacing an existing file", false).build(),
                (args, context) -> {
                    String profileName = ToolSupport.optionalString(args, "profile", "");
                    CredentialProfile profile = profile(credentials, profileName, false);
                    URI uri = profile == null ? URI.create(ToolSupport.requiredString(args, "url"))
                            : resolveProfileUri(profile, ToolSupport.requiredString(args, "path"));
                    Path destination = context.resolve(ToolSupport.requiredString(args, "destination"));
                    boolean overwrite = ToolSupport.optionalBool(args, "overwrite", false);
                    if (Files.exists(destination) && !overwrite)
                        throw new IllegalStateException("Destination exists: " + destination);
                    byte[] body = HTTP.send(uri, "GET", null, headers(profile), 25 * 1024 * 1024, profile != null && profile.allowPrivateNetwork()).body();
                    if (destination.getParent() != null) Files.createDirectories(destination.getParent());
                    Path temporary = Files.createTempFile(destination.getParent(), ".myclaw-download-", ".tmp");
                    try {
                        Files.write(temporary, body);
                        if (overwrite) Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                        else Files.move(temporary, destination);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                    return "Downloaded " + body.length + " bytes to " + destination;
                });
    }

    public static Tool batchFetch(Map<String, CredentialProfile> credentials) {
        return tool("batch_fetch", "Fetch multiple URLs or numbered pages concurrently with bounded concurrency and aggregate response limits.",
                JsonSchema.object().arrayOfStrings("urls", "Explicit URLs", false)
                        .string("urlTemplate", "Optional URL containing {page}", false)
                        .integer("startPage", "First page, defaults to 1", false)
                        .integer("pages", "Page count, 1-20", false)
                        .string("profile", "Optional backend credential profile", false)
                        .integer("concurrency", "Parallel requests, 1-5", false).build(),
                (args, context) -> {
                    CredentialProfile profile = profile(credentials, ToolSupport.optionalString(args, "profile", ""), false);
                    List<URI> uris = batchUris(args, profile);
                    int concurrency = ToolSupport.optionalInt(args, "concurrency", 3);
                    if (concurrency < 1 || concurrency > 5)
                        throw new IllegalArgumentException("concurrency must be between 1 and 5");
                    try (var executor = Executors.newFixedThreadPool(concurrency)) {
                        List<Callable<String>> tasks = uris.stream().<Callable<String>>map(uri -> () -> {
                            try {
                                return "URL: " + uri + "\n" + responseText(HTTP.send(uri, "GET", null, headers(profile), 1_000_000, profile != null && profile.allowPrivateNetwork()));
                            } catch (Exception exception) {
                                return "URL: " + uri + "\nERROR: " + exception.getMessage();
                            }
                        }).toList();
                        StringBuilder output = new StringBuilder();
                        for (var future : executor.invokeAll(tasks)) {
                            if (!output.isEmpty()) output.append("\n\n---\n\n");
                            output.append(future.get());
                            if (output.length() >= 60_000) break;
                        }
                        return ToolSupport.truncate(output.toString(), 60_000);
                    }
                });
    }

    private static List<URI> batchUris(JsonNode args, CredentialProfile profile) {
        List<URI> result = new ArrayList<>();
        JsonNode urls = args == null ? null : args.get("urls");
        if (urls != null && urls.isArray()) for (JsonNode url : urls) {
            if (!url.isString()) throw new IllegalArgumentException("Every URL must be a string");
            result.add(profile == null ? URI.create(url.asString()) : resolveProfileUri(profile, url.asString()));
        }
        String template = ToolSupport.optionalString(args, "urlTemplate", "");
        if (!template.isBlank()) {
            int start = ToolSupport.optionalInt(args, "startPage", 1);
            int pages = ToolSupport.optionalInt(args, "pages", 1);
            if (start < 1 || pages < 1 || pages > 20)
                throw new IllegalArgumentException("startPage must be positive and pages must be 1-20");
            for (int page = start; page < start + pages; page++) {
                String value = template.replace("{page}", String.valueOf(page));
                result.add(profile == null ? URI.create(value) : resolveProfileUri(profile, value));
            }
        }
        if (result.isEmpty() || result.size() > 20)
            throw new IllegalArgumentException("Provide between 1 and 20 URLs/pages");
        return result;
    }

    private static CredentialProfile profile(Map<String, CredentialProfile> profiles, String name, boolean required) {
        if (name == null || name.isBlank()) {
            if (required) throw new IllegalArgumentException("profile is required");
            return null;
        }
        CredentialProfile result = profiles == null ? null : profiles.get(name);
        if (result == null) throw new IllegalArgumentException("Unknown credential profile: " + name);
        return result;
    }

    private static Map<String, String> headers(CredentialProfile profile) {
        return profile == null ? Map.of() : profile.headers();
    }

    private static URI resolveProfileUri(CredentialProfile profile, String path) {
        if (profile.baseUrl() == null || profile.baseUrl().isBlank())
            throw new IllegalStateException("Credential profile has no baseUrl");
        URI base = URI.create(profile.baseUrl().endsWith("/") ? profile.baseUrl() : profile.baseUrl() + "/");
        URI resolved = base.resolve(path.startsWith("/") ? path.substring(1) : path);
        if (!sameOrigin(base, resolved)) throw new SecurityException("API path escapes the credential profile origin");
        return resolved;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme()) && left.getHost().equalsIgnoreCase(right.getHost()) && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String responseText(SecureHttp.Response response) {
        String text = new String(response.body(), StandardCharsets.UTF_8);
        if (response.status() >= 400)
            throw new IllegalStateException("HTTP " + response.status() + ": " + ToolSupport.truncate(text, 500));
        return ToolSupport.truncate(HttpFetchTool.htmlToText(text), 20_000);
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

    @FunctionalInterface
    private interface Call {
        String run(JsonNode args, ToolContext context) throws Exception;
    }

    static final class SecureHttp {
        private static final int MAX_REDIRECTS = 5;
        private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
        private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
        private final boolean allowPrivateNetwork;
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

        record Response(int status, URI uri, java.net.http.HttpHeaders headers, byte[] body) {
        }

        SecureHttp() {
            this(false);
        }

        SecureHttp(boolean allowPrivateNetwork) {
            this.allowPrivateNetwork = allowPrivateNetwork;
        }

        Response send(URI initial, String method, String body, Map<String, String> headers, int maxBytes) throws Exception {
            return send(initial, method, body, headers, maxBytes, false);
        }

        Response send(URI initial, String method, String body, Map<String, String> headers, int maxBytes, boolean requestAllowsPrivate) throws Exception {
            URI uri = initial;
            URI credentialOrigin = headers.isEmpty() ? null : initial;
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                if (!allowPrivateNetwork && !requestAllowsPrivate) validatePublic(uri);
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("User-Agent", "MyClaw/1.0");
                headers.forEach((name, value) -> {
                    if (isAllowedHeader(name)) builder.header(name, value);
                });
                builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                HttpResponse<java.io.InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 == 3) {
                    String location = response.headers().firstValue("location").orElseThrow(() -> new IllegalStateException("Redirect has no Location header"));
                    response.body().close();
                    URI redirected = uri.resolve(location);
                    if (credentialOrigin != null && !sameOrigin(credentialOrigin, redirected))
                        throw new SecurityException("Refusing to forward authentication headers across origins");
                    uri = redirected;
                    continue;
                }
                try (var input = response.body()) {
                    byte[] bytes = input.readNBytes(maxBytes + 1);
                    if (bytes.length > maxBytes)
                        throw new IllegalStateException("Response exceeds " + maxBytes + " bytes");
                    return new Response(response.statusCode(), uri, response.headers(), bytes);
                }
            }
            throw new IllegalStateException("Too many HTTP redirects");
        }

        private static void validatePublic(URI uri) throws Exception {
            if (uri == null || uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException("Only absolute HTTP/HTTPS URLs are allowed");
            if (uri.getUserInfo() != null) throw new SecurityException("Credentials in URLs are not allowed");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress())
                    throw new SecurityException("Private or local network address is not allowed: " + uri.getHost());
            }
        }

        private static boolean isAllowedHeader(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            return !List.of("host", "content-length", "connection", "transfer-encoding").contains(lower);
        }
    }
}
