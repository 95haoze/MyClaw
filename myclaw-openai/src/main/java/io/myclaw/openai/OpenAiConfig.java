package io.myclaw.openai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 兼容端点的连接配置。
 *
 * <p>MyClaw 只实现一套 <b>OpenAI Chat Completions 兼容协议</b>，因此同一个类可以直接对接：
 * <table border="1">
 *   <caption>常见服务商</caption>
 *   <tr><th>服务商</th><th>baseUrl</th><th>示例模型</th></tr>
 *   <tr><td>OpenAI</td><td>https://api.openai.com/v1</td><td>gpt-4o-mini</td></tr>
 *   <tr><td>通义千问（DashScope）</td><td>https://dashscope.aliyuncs.com/compatible-mode/v1</td><td>qwen-plus</td></tr>
 *   <tr><td>DeepSeek</td><td>https://api.deepseek.com/v1</td><td>deepseek-chat</td></tr>
 *   <tr><td>Ollama（本地）</td><td>http://localhost:11434/v1</td><td>qwen2.5:7b</td></tr>
 * </table>
 *
 * <p>用 {@link #fromEnv()} 可以自动从环境变量推断配置，适合零配置启动。
 */
public record OpenAiConfig(
        String baseUrl,
        String apiKey,
        String model,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Duration timeout,
        int maxRetries,
        String organization,
        Map<String, String> extraHeaders,
        boolean includeUsageInStream) {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final String DASHSCOPE_MODEL = "qwen-plus";
    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";
    public static final String DEEPSEEK_MODEL = "deepseek-chat";

    public OpenAiConfig {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : stripTrailingSlash(baseUrl);
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        timeout = timeout == null ? Duration.ofSeconds(120) : timeout;
        extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
    }

    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------- 预设 ----------------------------------
    /** 通义千问（DashScope OpenAI 兼容模式）。 */
    public static Builder dashscope(String apiKey) {
        return builder().baseUrl(DASHSCOPE_BASE_URL).apiKey(apiKey).model(DASHSCOPE_MODEL);
    }

    /** DeepSeek。 */
    public static Builder deepseek(String apiKey) {
        return builder().baseUrl(DEEPSEEK_BASE_URL).apiKey(apiKey).model(DEEPSEEK_MODEL);
    }

    /** 本地 Ollama。 */
    public static Builder ollama() {
        return ollama("http://localhost:11434/v1", "qwen2.5:7b");
    }

    public static Builder ollama(String baseUrl, String model) {
        return builder().baseUrl(baseUrl).apiKey("ollama").model(model);
    }

    /**
     * 从环境变量推断配置，按优先级依次尝试：
     * <ol>
     *   <li>API Key：{@code MYCLAW_API_KEY} → {@code OPENAI_API_KEY} → {@code DASHSCOPE_API_KEY}
     *       → {@code aliQwen-api}（阿里云 SDK 的默认变量名）</li>
     *   <li>Base URL：{@code MYCLAW_BASE_URL} → {@code OPENAI_BASE_URL}；若 Key 来自 DashScope
     *       则自动使用 DashScope 端点</li>
     *   <li>模型：{@code MYCLAW_MODEL} → {@code OPENAI_MODEL}；未设置时按服务商给默认值</li>
     * </ol>
     */
    public static OpenAiConfig fromEnv() {
        String dashscopeKey = firstNonBlank(System.getenv("DASHSCOPE_API_KEY"), System.getenv("aliQwen-api"));
        String apiKey = firstNonBlank(System.getenv("MYCLAW_API_KEY"), System.getenv("OPENAI_API_KEY"), dashscopeKey);

        boolean dashscopeOnly = apiKey != null && apiKey.equals(dashscopeKey)
                && System.getenv("OPENAI_API_KEY") == null && System.getenv("MYCLAW_API_KEY") == null;

        String baseUrl = firstNonBlank(System.getenv("MYCLAW_BASE_URL"), System.getenv("OPENAI_BASE_URL"));
        String model = firstNonBlank(System.getenv("MYCLAW_MODEL"), System.getenv("OPENAI_MODEL"));

        return builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl != null ? baseUrl : (dashscopeOnly ? DASHSCOPE_BASE_URL : DEFAULT_BASE_URL))
                .model(model != null ? model : (dashscopeOnly ? DASHSCOPE_MODEL : DEFAULT_MODEL))
                .build();
    }

    // ------------------------------------------------------------------ 派生

    public Builder toBuilder() {
        return new Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .topP(topP)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .organization(organization)
                .extraHeaders(extraHeaders)
                .includeUsageInStream(includeUsageInStream);
    }

    /** {@code /chat/completions} 的完整地址。 */
    public String chatCompletionsUrl() {
        return baseUrl + "/chat/completions";
    }

    /** 配置是否完整（至少要有 API Key）。 */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 用于日志的脱敏描述，绝不打印 Key 本身。 */
    public String describe() {
        String masked = apiKey == null || apiKey.isBlank()
                ? "<未配置>"
                : apiKey.substring(0, Math.min(6, apiKey.length())) + "****";
        return "OpenAiConfig(baseUrl=" + baseUrl + ", model=" + model + ", apiKey=" + masked + ")";
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Builder

    public static final class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String apiKey;
        private String model = DEFAULT_MODEL;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Duration timeout = Duration.ofSeconds(120);
        private int maxRetries = 2;
        private String organization;
        private final Map<String, String> extraHeaders = new LinkedHashMap<>();
        private boolean includeUsageInStream = true;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Math.max(0, maxRetries);
            return this;
        }

        public Builder organization(String organization) {
            this.organization = organization;
            return this;
        }

        public Builder header(String name, String value) {
            this.extraHeaders.put(name, value);
            return this;
        }

        public Builder extraHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.extraHeaders.putAll(headers);
            }
            return this;
        }

        public Builder includeUsageInStream(boolean includeUsageInStream) {
            this.includeUsageInStream = includeUsageInStream;
            return this;
        }

        public OpenAiConfig build() {
            return new OpenAiConfig(baseUrl, apiKey, model, temperature, maxTokens, topP,
                    timeout, maxRetries, organization, extraHeaders, includeUsageInStream);
        }
    }
}
