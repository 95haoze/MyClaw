package io.myclaw.spring;

import io.myclaw.core.agent.Agent;
import io.myclaw.core.agent.AgentPrompts;
import io.myclaw.openai.OpenAiConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MyClaw 的全部外部配置，前缀 {@code myclaw.*}。
 *
 * <p>最小可用配置（只填 Key，其余全有合理默认值）：
 * <pre>{@code
 * myclaw:
 *   api-key: ${DASHSCOPE_API_KEY}
 * }</pre>
 *
 * <p>完整配置示例见项目 README。
 */
@ConfigurationProperties(prefix = "myclaw")
public class MyClawProperties {

    /**
     * 是否启用 MyClaw 自动配置。
     */
    private boolean enabled = true;

    /**
     * OpenAI 兼容端点地址；留空则按 API Key 来源自动推断。
     */
    private String baseUrl;

    /**
     * API Key。推荐通过环境变量注入，不要硬编码在配置文件里。
     */
    private String apiKey;

    /**
     * 模型名。
     */
    private String model;

    /**
     * 采样温度。
     */
    private Double temperature = 0.7;

    /**
     * 最大输出 token 数；留空交给服务端默认。
     */
    private Integer maxTokens;

    /**
     * 单次 HTTP 请求超时。
     */
    private Duration timeout = Duration.ofSeconds(120);

    /**
     * 对 429 / 5xx 的最大重试次数。
     */
    private int maxRetries = 2;

    /**
     * 系统提示词。
     */
    private String systemPrompt = AgentPrompts.DEFAULT;

    /**
     * Agent 单次请求的最大模型调用轮数，防止工具调用死循环。
     */
    private int maxIterations = Agent.DEFAULT_MAX_ITERATIONS;

    /**
     * 工具的工作目录，文件类工具只能访问该目录及其子目录。
     */
    private String workingDirectory = ".";

    /**
     * 是否注册一个把关键动作打进日志的 LoggingHook。
     */
    private boolean loggingHook = true;

    private final Memory memory = new Memory();

    private final Tools tools = new Tools();

    private final Skills skills = new Skills();

    // ------------------------------- 嵌套配置 -------------------------------------

    /**
     * 记忆相关配置。
     */
    public static class Memory {

        /**
         * 保留的最大消息条数；{@code <= 0} 表示不限制。
         */
        private int maxMessages = 40;

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }
    }

    /**
     * 内置工具开关。生产环境建议按最小权限原则只开需要的。
     */
    public static class Tools {

        /**
         * 内置工具总开关。
         */
        private boolean builtinsEnabled = true;

        /**
         * 计算器 {@code calculate}。
         */
        private boolean calculator = true;

        /**
         * 当前时间 {@code current_time}。
         */
        private boolean currentTime = true;

        /**
         * 读文件 {@code read_file}。
         */
        private boolean fileRead = true;

        /**
         * 写文件 {@code write_file}。
         */
        private boolean fileWrite = true;

        /**
         * 列目录 {@code list_files}。
         */
        private boolean fileList = true;

        /**
         * Repository text search.
         */
        private boolean searchText = true;

        /**
         * Exact, guarded text replacement.
         */
        private boolean replaceText = true;

        /**
         * 网页抓取 {@code http_fetch}。
         */
        private boolean httpFetch = true;

        /**
         * Structured Git tools.
         */
        private boolean git = true;

        /**
         * Java AST code intelligence tools.
         */
        private boolean codeAnalysis = true;

        /**
         * Database inspection/query tools backed by Spring DataSource beans.
         */
        private boolean database = true;
        private boolean databaseAllowWrite = false;
        private int databaseMaxRows = 200;
        private int databaseTimeoutSeconds = 15;

        /**
         * Administrator-controlled language server commands keyed by LSP language id.
         */
        private Map<String, List<String>> lspServers = new LinkedHashMap<>();

        /**
         * Named server-side HTTP credential profiles.
         */
        private Map<String, HttpCredential> httpCredentials = new LinkedHashMap<>();

        /**
         * Named web search providers.
         */
        private Map<String, SearchProvider> searchProviders = new LinkedHashMap<>();

        /**
         * 执行命令 {@code run_command} —— <b>默认关闭</b>，开启前请确认风险。
         */
        private boolean shell = false;

        /**
         * Execute generated scripts. Disabled by default because this is not an OS sandbox.
         */
        private boolean codeExecute = false;

        /**
         * Shell 命令白名单前缀；为空表示不限制前缀（仍会拦截明确的高危命令）。
         */
        private List<String> shellAllowlist = new ArrayList<>();

        /**
         * Shell 命令执行超时。
         */
        private Duration shellTimeout = Duration.ofSeconds(30);

        /**
         * 是否启用后台进程与结构化项目工具。
         */
        private boolean backgroundProcesses = false;
        private boolean projectTools = true;

        /**
         * 是否启用 Playwright 浏览器工具。默认关闭，需预先安装 Chromium。
         */
        private boolean browser = false;
        private boolean browserHeadless = true;
        private boolean browserAllowPrivateNetwork = false;
        private List<String> browserAllowedHosts = new ArrayList<>();

        public boolean isBuiltinsEnabled() {
            return builtinsEnabled;
        }

        public void setBuiltinsEnabled(boolean builtinsEnabled) {
            this.builtinsEnabled = builtinsEnabled;
        }

        public boolean isCalculator() {
            return calculator;
        }

        public void setCalculator(boolean calculator) {
            this.calculator = calculator;
        }

        public boolean isCurrentTime() {
            return currentTime;
        }

        public void setCurrentTime(boolean currentTime) {
            this.currentTime = currentTime;
        }

        public boolean isFileRead() {
            return fileRead;
        }

        public void setFileRead(boolean fileRead) {
            this.fileRead = fileRead;
        }

        public boolean isFileWrite() {
            return fileWrite;
        }

        public void setFileWrite(boolean fileWrite) {
            this.fileWrite = fileWrite;
        }

        public boolean isFileList() {
            return fileList;
        }

        public void setFileList(boolean fileList) {
            this.fileList = fileList;
        }


        public boolean isSearchText() {
            return searchText;
        }

        public void setSearchText(boolean searchText) {
            this.searchText = searchText;
        }

        public boolean isReplaceText() {
            return replaceText;
        }

        public void setReplaceText(boolean replaceText) {
            this.replaceText = replaceText;
        }

        public Map<String, HttpCredential> getHttpCredentials() {
            return httpCredentials;
        }

        public void setHttpCredentials(Map<String, HttpCredential> value) {
            httpCredentials = value == null ? new LinkedHashMap<>() : value;
        }

        public Map<String, SearchProvider> getSearchProviders() {
            return searchProviders;
        }

        public void setSearchProviders(Map<String, SearchProvider> value) {
            searchProviders = value == null ? new LinkedHashMap<>() : value;
        }

        public static class HttpCredential {
            private String baseUrl;
            private Map<String, String> headers = new LinkedHashMap<>();
            private boolean allowPrivateNetwork;

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public boolean isAllowPrivateNetwork() {
                return allowPrivateNetwork;
            }

            public void setAllowPrivateNetwork(boolean value) {
                allowPrivateNetwork = value;
            }

            public Map<String, String> getHeaders() {
                return headers;
            }

            public void setHeaders(Map<String, String> headers) {
                this.headers = headers == null ? new LinkedHashMap<>() : headers;
            }
        }

        public static class SearchProvider {
            private String urlTemplate;
            private String credentialProfile;
            private String responseFormat = "auto";
            private String method = "GET";

            public String getUrlTemplate() {
                return urlTemplate;
            }

            public void setUrlTemplate(String urlTemplate) {
                this.urlTemplate = urlTemplate;
            }

            public String getCredentialProfile() {
                return credentialProfile;
            }

            public void setCredentialProfile(String credentialProfile) {
                this.credentialProfile = credentialProfile;
            }

            public String getResponseFormat() {
                return responseFormat;
            }

            public void setResponseFormat(String responseFormat) {
                this.responseFormat = responseFormat;
            }

            public String getMethod() {
                return method;
            }

            public void setMethod(String method) {
                this.method = method;
            }
        }

        public Map<String, List<String>> getLspServers() {
            return lspServers;
        }

        public void setLspServers(Map<String, List<String>> lspServers) {
            this.lspServers = lspServers == null ? new LinkedHashMap<>() : lspServers;
        }

        public boolean isDatabase() {
            return database;
        }

        public void setDatabase(boolean database) {
            this.database = database;
        }

        public boolean isDatabaseAllowWrite() {
            return databaseAllowWrite;
        }

        public void setDatabaseAllowWrite(boolean value) {
            databaseAllowWrite = value;
        }

        public int getDatabaseMaxRows() {
            return databaseMaxRows;
        }

        public void setDatabaseMaxRows(int value) {
            databaseMaxRows = value;
        }

        public int getDatabaseTimeoutSeconds() {
            return databaseTimeoutSeconds;
        }

        public void setDatabaseTimeoutSeconds(int value) {
            databaseTimeoutSeconds = value;
        }

        public boolean isCodeAnalysis() {
            return codeAnalysis;
        }

        public void setCodeAnalysis(boolean codeAnalysis) {
            this.codeAnalysis = codeAnalysis;
        }

        public boolean isGit() {
            return git;
        }

        public void setGit(boolean git) {
            this.git = git;
        }

        public boolean isHttpFetch() {
            return httpFetch;
        }

        public void setHttpFetch(boolean httpFetch) {
            this.httpFetch = httpFetch;
        }

        public boolean isCodeExecute() {
            return codeExecute;
        }

        public void setCodeExecute(boolean codeExecute) {
            this.codeExecute = codeExecute;
        }

        public boolean isShell() {
            return shell;
        }

        public void setShell(boolean shell) {
            this.shell = shell;
        }

        public List<String> getShellAllowlist() {
            return shellAllowlist;
        }

        public void setShellAllowlist(List<String> shellAllowlist) {
            this.shellAllowlist = shellAllowlist == null ? new ArrayList<>() : shellAllowlist;
        }

        public Duration getShellTimeout() {
            return shellTimeout;
        }

        public void setShellTimeout(Duration shellTimeout) {
            this.shellTimeout = shellTimeout;
        }

        public boolean isBackgroundProcesses() {
            return backgroundProcesses;
        }

        public void setBackgroundProcesses(boolean value) {
            backgroundProcesses = value;
        }

        public boolean isProjectTools() {
            return projectTools;
        }

        public void setProjectTools(boolean value) {
            projectTools = value;
        }

        public boolean isBrowser() {
            return browser;
        }

        public void setBrowser(boolean value) {
            browser = value;
        }

        public boolean isBrowserHeadless() {
            return browserHeadless;
        }

        public void setBrowserHeadless(boolean value) {
            browserHeadless = value;
        }

        public boolean isBrowserAllowPrivateNetwork() {
            return browserAllowPrivateNetwork;
        }

        public void setBrowserAllowPrivateNetwork(boolean value) {
            browserAllowPrivateNetwork = value;
        }

        public List<String> getBrowserAllowedHosts() {
            return browserAllowedHosts;
        }

        public void setBrowserAllowedHosts(List<String> value) {
            browserAllowedHosts = value == null ? new ArrayList<>() : value;
        }
    }

    /**
     * 按需加载技能相关配置。
     */
    public static class Skills {
        private boolean enabled = true;
        private List<String> directories = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getDirectories() {
            return directories;
        }

        public void setDirectories(List<String> directories) {
            this.directories = directories == null ? new ArrayList<>() : directories;
        }
    }
    // -------------------------------- 派生 ----------------------------------

    /**
     * 转换成 {@link OpenAiConfig}。
     *
     * <p>若 {@code api-key} 未在配置文件里给出，会退回到 {@link OpenAiConfig#fromEnv()}，
     * 从环境变量（{@code MYCLAW_API_KEY} / {@code OPENAI_API_KEY} / {@code DASHSCOPE_API_KEY} /
     * {@code aliQwen-api}）推断，并据此自动选择默认的 baseUrl 与模型。
     */
    public OpenAiConfig toOpenAiConfig() {
        boolean keyFromProperties = apiKey != null && !apiKey.isBlank();
        OpenAiConfig.Builder builder = keyFromProperties
                ? OpenAiConfig.builder()
                : OpenAiConfig.fromEnv().toBuilder();

        if (keyFromProperties) {
            builder.apiKey(apiKey.strip());
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl.strip());
        }
        if (model != null && !model.isBlank()) {
            builder.model(model.strip());
        }
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (timeout != null) {
            builder.timeout(timeout);
        }
        builder.maxRetries(maxRetries);
        return builder.build();
    }

    // ------------------------------ getter / setter ------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public boolean isLoggingHook() {
        return loggingHook;
    }

    public void setLoggingHook(boolean loggingHook) {
        this.loggingHook = loggingHook;
    }

    public Memory getMemory() {
        return memory;
    }

    public Tools getTools() {
        return tools;
    }

    public Skills getSkills() {
        return skills;
    }
}
