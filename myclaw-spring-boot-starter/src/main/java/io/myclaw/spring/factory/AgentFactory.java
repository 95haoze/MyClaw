package io.myclaw.spring.factory;

import io.myclaw.core.agent.Agent;
import io.myclaw.core.agent.AgentPrompts;
import io.myclaw.core.agent.LoggingHook;
import io.myclaw.core.memory.InMemoryMemory;
import io.myclaw.core.message.Message;
import io.myclaw.core.model.ChatModel;
import io.myclaw.core.model.ChatOptions;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolRegistry;
import io.myclaw.spring.MyClawProperties;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** 创建彼此独立、拥有各自 Memory 的 Agent。 */
public class AgentFactory {

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final MyClawProperties properties;

    public AgentFactory(ChatModel chatModel,
                        ToolRegistry toolRegistry,
                        MyClawProperties properties) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public Agent create(String name) {
        return create(name, List.of());
    }

    public Agent create(String name, Collection<Message> initialMessages) {
        return create(name, initialMessages, Path.of(properties.getWorkingDirectory()), "workspace-write");
    }

    public Agent create(String name, Collection<Message> initialMessages, Path workingDirectory) {
        return create(name, initialMessages, workingDirectory, "workspace-write" );
    }

    public Agent create(String name, Collection<Message> initialMessages, Path workingDirectory, String permissionMode) {
        InMemoryMemory memory = new InMemoryMemory(properties.getMemory().getMaxMessages());
        memory.addAll(initialMessages);

        Agent.Builder builder = Agent.builder(name)
                .model(chatModel)
                .tools(toolsFor(permissionMode))
                .memory(memory)
                .systemPrompt(resolveSystemPrompt(workingDirectory))
                .maxIterations(properties.getMaxIterations())
                .options(ChatOptions.builder()
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .build())
                .toolContext(ToolContext.of(workingDirectory));

        if (properties.isLoggingHook()) {
            builder.hook(new LoggingHook());
        }

        return builder.build();
    }


    private ToolRegistry toolsFor(String mode) {
        if ("full-access".equals(mode)) return toolRegistry;
        java.util.Set<String> blocked = "read-only".equals(mode) ? java.util.Set.of(
                "write_file", "replace_text", "batch_replace_text", "file_manage", "run_command",
                "execute_code", "download_file", "database_execute", "git_add", "git_reset",
                "git_commit", "git_branch", "git_checkout") : java.util.Set.of("database_execute");
        return toolRegistry.filtered(name -> !blocked.contains(name));
    }
    private String resolveSystemPrompt(Path workingDirectory) {
        String prompt = properties.getSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            prompt = AgentPrompts.DEFAULT;
        }

        MyClawProperties.Tools tools = properties.getTools();
        boolean hasFileTools = tools.isBuiltinsEnabled()
                && (tools.isFileRead() || tools.isFileWrite() || tools.isFileList());

        if (hasFileTools && AgentPrompts.DEFAULT.equals(prompt)) {
            boolean codingTools = tools.isSearchText() && tools.isReplaceText() && tools.isShell();
            return codingTools
                    ? AgentPrompts.codingWithWorkingDirectory(workingDirectory)
                    : AgentPrompts.withWorkingDirectory(workingDirectory);
        }
        return prompt;
    }
}
