package io.myclaw.spring;

import io.myclaw.core.agent.Agent;
import io.myclaw.core.agent.AgentPrompts;
import io.myclaw.core.agent.LoggingHook;
import io.myclaw.core.memory.InMemoryMemory;
import io.myclaw.core.skill.ListSkillsTool;
import io.myclaw.core.skill.LoadSkillTool;
import io.myclaw.core.skill.SkillRegistry;
import io.myclaw.core.model.ChatModel;
import io.myclaw.core.model.ChatOptions;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolRegistry;
import io.myclaw.openai.OpenAiChatModel;
import io.myclaw.openai.OpenAiConfig;
import io.myclaw.spring.factory.AgentFactory;
import io.myclaw.tools.BuiltinTools;
import io.myclaw.tools.BackgroundProcessTools;
import io.myclaw.tools.BrowserAutomationTools;
import io.myclaw.tools.NetworkTools;
import io.myclaw.tools.DatabaseTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * MyClaw 的 Spring Boot 自动配置。
 *
 * <p>引入依赖并配置好 {@code myclaw.api-key} 之后，就能直接注入一个开箱即用的
 * {@link Agent}：
 * <pre>{@code
 * @RestController
 * public class ChatController {
 *     private final Agent agent;
 *
 *     public ChatController(Agent agent) { this.agent = agent; }
 *
 *     @GetMapping("/chat")
 *     public String chat(@RequestParam String q) {
 *         return agent.run(q).content();
 *     }
 * }
 * }</pre>
 *
 * <p>所有 Bean 都带 {@link ConditionalOnMissingBean}，因此你可以随时用同类型的自定义 Bean
 * 覆盖其中任意一环（自定义 {@link ChatModel}、追加工具、替换 {@link ToolRegistry} 等）。
 */
@AutoConfiguration
@ConditionalOnClass({Agent.class, OpenAiChatModel.class})
@ConditionalOnProperty(prefix = "myclaw", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MyClawProperties.class)
public class MyClawAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MyClawAutoConfiguration.class);

    /** OpenAI 兼容端点的连接配置。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenAiConfig myClawOpenAiConfig(MyClawProperties properties) {
        OpenAiConfig config = properties.toOpenAiConfig();
        log.info("MyClaw 模型配置: {}", config.describe());
        if (!config.hasApiKey()) {
            log.warn("MyClaw 没有拿到 API Key，请设置 myclaw.api-key 或环境变量 "
                    + "MYCLAW_API_KEY / OPENAI_API_KEY / DASHSCOPE_API_KEY");
        }
        return config;
    }

    /** 默认使用 OpenAI 兼容协议的模型实现。 */
    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel myClawChatModel(OpenAiConfig openAiConfig) {
        return new OpenAiChatModel(openAiConfig);
    }

    /**
     * 工具注册表：先装入按配置启用的内置工具，随后由 {@link MyClawToolRegistrar}
     * 把容器里所有 {@code @MyClawTool} 方法也注册进来。
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry myClawToolRegistry(MyClawProperties properties, SkillRegistry skillRegistry, ApplicationContext applicationContext) {
        ToolRegistry registry = new ToolRegistry();
        registerBuiltins(registry, properties.getTools(), applicationContext);
        if (properties.getSkills().isEnabled()) {
            registry.registerAll(new ListSkillsTool(skillRegistry), new LoadSkillTool(skillRegistry));
        }
        log.info("MyClaw 内置工具已注册: {}", registry.names());
        return registry;
    }


    /** 技能注册表：先扫描配置目录，随后 Registrar 加入代码声明的技能。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry myClawSkillRegistry(MyClawProperties properties) {
        SkillRegistry registry = new SkillRegistry();
        if (properties.getSkills().isEnabled()) {
            for (String directory : properties.getSkills().getDirectories()) {
                if (directory != null && !directory.isBlank()) {
                    registry.scan(Path.of(directory.strip()));
                }
            }
        }
        return registry;
    }
    /** 开箱即用的 Agent。 */
    @Bean
    @ConditionalOnMissingBean
    public Agent myClawAgent(AgentFactory agentFactory) {
        return agentFactory.create("myclaw");
    }

    /** 扫描 {@code @MyClawTool} 方法并注册成工具。 */
    @Bean
    @ConditionalOnMissingBean
    public MyClawToolRegistrar myClawToolRegistrar(ApplicationContext applicationContext, ToolRegistry toolRegistry) {
        return new MyClawToolRegistrar(applicationContext, toolRegistry);
    }

    /** 收集 Skill Bean 与 @MyClawSkill 方法。 */
    @Bean
    @ConditionalOnMissingBean
    public MyClawSkillRegistrar myClawSkillRegistrar(ApplicationContext applicationContext,
                                                      SkillRegistry skillRegistry) {
        return new MyClawSkillRegistrar(applicationContext, skillRegistry);
    }

    /** agent工厂, 复制创建新的 agent 实例，防止出现多个会话同时共享一个 Memory */
    @Bean
    @ConditionalOnMissingBean
    public AgentFactory myClawAgentFactory(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            MyClawProperties properties
    ) {
        return new AgentFactory(chatModel, toolRegistry, properties);
    }
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "myclaw.tools", name = "background-processes", havingValue = "true")
    public BackgroundProcessTools myClawBackgroundProcessTools(MyClawProperties properties) {
        return BuiltinTools.backgroundProcesses(properties.getTools().getShellAllowlist());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "myclaw.tools", name = "browser", havingValue = "true")
    public BrowserAutomationTools myClawBrowserTools(MyClawProperties properties) {
        var tools = properties.getTools();
        return BuiltinTools.browser(tools.getBrowserAllowedHosts(), tools.isBrowserAllowPrivateNetwork(), tools.isBrowserHeadless());
    }

    // ------------------------------------------------------------------ 内部

    private static void registerBuiltins(ToolRegistry registry, MyClawProperties.Tools tools, ApplicationContext applicationContext) {
        if (!tools.isBuiltinsEnabled()) {
            return;
        }
        if (tools.isCalculator()) {
            registry.register(BuiltinTools.calculator());
        }
        if (tools.isCurrentTime()) {
            registry.register(BuiltinTools.currentTime());
        }
        if (tools.isFileRead()) {
            registry.register(BuiltinTools.readFile());
        }
        if (tools.isFileWrite()) {
            registry.register(BuiltinTools.writeFile());
        }
        if (tools.isFileList()) {
            registry.register(BuiltinTools.listFiles());
        }
        if (tools.isSearchText()) {
            registry.register(BuiltinTools.searchText());
        }
        if (tools.isReplaceText()) {
            registry.register(BuiltinTools.replaceText());
            registry.register(BuiltinTools.batchReplaceText());
        }
        if (tools.isFileWrite()) {
            registry.register(BuiltinTools.manageFile());
        }
        if (!tools.getLspServers().isEmpty()) {
            registry.register(BuiltinTools.lspQuery(tools.getLspServers()));
        }        if (tools.isCodeAnalysis()) {
            registry.register(BuiltinTools.findSymbol());
            registry.register(BuiltinTools.findReferences());
            registry.register(BuiltinTools.codeOutline());
            registry.register(BuiltinTools.codeDiagnostics());
        }        if (tools.isGit()) {
            registry.register(BuiltinTools.gitStatus());
            registry.register(BuiltinTools.gitDiff());
            registry.register(BuiltinTools.gitLog());
            registry.register(BuiltinTools.gitAdd());
            registry.register(BuiltinTools.gitReset());
            registry.register(BuiltinTools.gitCommit());
            registry.register(BuiltinTools.gitBranch());
            registry.register(BuiltinTools.gitCheckout());
            registry.register(BuiltinTools.gitShow());
            registry.register(BuiltinTools.gitBlame());
            registry.register(BuiltinTools.gitStash());
            registry.register(BuiltinTools.gitTag());
        }
        if (tools.isProjectTools()) {
            registry.register(BuiltinTools.projectDetect());
            registry.register(BuiltinTools.projectBuild());
            registry.register(BuiltinTools.projectTests());
            registry.register(BuiltinTools.projectTestCase());
            registry.register(BuiltinTools.projectLint());
        }
        if (tools.isBackgroundProcesses()) {
            applicationContext.getBeansOfType(BackgroundProcessTools.class).values()
                    .forEach(processTools -> registry.registerAll(processTools.tools()));
        }
        if (tools.isBrowser()) {
            applicationContext.getBeansOfType(BrowserAutomationTools.class).values()
                    .forEach(browserTools -> registry.registerAll(browserTools.tools()));
        }
        if (tools.isDatabase()) {
            java.util.Map<String, DatabaseTools.Profile> databases = new java.util.LinkedHashMap<>();
            applicationContext.getBeansOfType(javax.sql.DataSource.class).forEach((name, dataSource) ->
                    databases.put(name, new DatabaseTools.Profile(dataSource, tools.isDatabaseAllowWrite(),
                            tools.getDatabaseMaxRows(), tools.getDatabaseTimeoutSeconds())));
            if (!databases.isEmpty()) {
                registry.register(BuiltinTools.databaseSchema(databases));
                registry.register(BuiltinTools.databaseQuery(databases));
                if (tools.isDatabaseAllowWrite()) {
                    registry.register(BuiltinTools.databaseExecute(databases));
                    log.warn("MyClaw db_execute is enabled; the Agent can modify configured DataSource contents");
                }
            }
        }        var credentials = networkCredentials(tools);
        registry.register(BuiltinTools.downloadFile(credentials));
        registry.register(BuiltinTools.batchFetch(credentials));
        if (!credentials.isEmpty()) registry.register(BuiltinTools.apiRequest(credentials));
        var searchProviders = searchProviders(tools);
        if (!searchProviders.isEmpty()) registry.register(BuiltinTools.webSearch(searchProviders, credentials));
        if (tools.isHttpFetch()) {
            registry.register(BuiltinTools.httpFetch());
        }
        if (tools.isCodeExecute()) {
            registry.register(BuiltinTools.executeCode(true));
            log.warn("MyClaw execute_code is enabled; scripts run with server account permissions");
        }
        if (tools.isShell()) {
            registry.register(BuiltinTools.shell(true, tools.getShellAllowlist(), tools.getShellTimeout()));
            log.warn("MyClaw 已启用 run_command 工具，Agent 将能够执行 Shell 命令，请确认这是预期行为");
        }
    }

    /**
     * 系统提示词：用户没自定义且确实开启了文件类工具时，自动把工作目录写进提示词，
     * 让模型知道相对路径的基准在哪里。
     */
    private static java.util.Map<String, NetworkTools.CredentialProfile> networkCredentials(MyClawProperties.Tools tools) {
        java.util.Map<String, NetworkTools.CredentialProfile> result = new java.util.LinkedHashMap<>();
        tools.getHttpCredentials().forEach((name, value) -> result.put(name,
                new NetworkTools.CredentialProfile(value.getBaseUrl(), value.getHeaders(), value.isAllowPrivateNetwork())));
        return result;
    }

    private static java.util.Map<String, NetworkTools.SearchProvider> searchProviders(MyClawProperties.Tools tools) {
        java.util.Map<String, NetworkTools.SearchProvider> result = new java.util.LinkedHashMap<>();
        tools.getSearchProviders().forEach((name, value) -> result.put(name,
                new NetworkTools.SearchProvider(value.getUrlTemplate(), value.getCredentialProfile(), value.getResponseFormat(), value.getMethod())));
        return result;
    }
    private static String resolveSystemPrompt(MyClawProperties properties) {
        String prompt = properties.getSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            prompt = AgentPrompts.DEFAULT;
        }
        MyClawProperties.Tools tools = properties.getTools();
        boolean hasFileTools = tools.isBuiltinsEnabled()
                && (tools.isFileRead() || tools.isFileWrite() || tools.isFileList());
        if (hasFileTools && AgentPrompts.DEFAULT.equals(prompt)) {
            return AgentPrompts.withWorkingDirectory(Path.of(properties.getWorkingDirectory()));
        }
        return prompt;
    }
}
