package io.myclaw.core.agent;

import io.myclaw.core.memory.InMemoryReflectionStore;
import io.myclaw.core.memory.Reflection;
import io.myclaw.core.memory.ReflectionStore;
import io.myclaw.core.message.Message;
import io.myclaw.core.message.Role;
import io.myclaw.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 反思钩子 —— Agent 的"复盘与经验沉淀"。
 *
 * <p>挂到 {@link Agent} 上之后，每次任务结束（无论成功还是异常）都会自动复盘，
 * 把结果写入 {@link ReflectionStore}。这些经验可以在下次任务前通过
 * {@link #recall(String, int)} 检索，再用 {@link #augmentSystemPrompt(String, List)}
 * 拼进系统提示词，让 Agent 记住并复用历史经验。
 *
 * <p>复盘本身<b>不调用模型</b>（纯规则式，确定性、零额外成本），因此不会影响主流程，
 * 也便于离线测试。如需更智能的反思，可通过 {@link Generator} 自定义生成逻辑。
 *
 * <p>用法：
 * <pre>{@code
 * ReflectionHook hook = new ReflectionHook(new InMemoryReflectionStore());
 * Agent agent = Agent.builder("assistant").model(model).hook(hook).build();
 *
 * // 下次任务前注入历史经验：
 * String prompt = ReflectionHook.augmentSystemPrompt(basePrompt, hook.recall("SQL 优化", 3));
 * }</pre>
 *
 * @see AgentHook
 * @see ReflectionStore
 */
public class ReflectionHook implements AgentHook {

    private static final Logger log = LoggerFactory.getLogger(ReflectionHook.class);

    private final ReflectionStore store;
    private final Generator generator;

    /** 使用内存存储与默认规则式复盘。 */
    public ReflectionHook() {
        this(new InMemoryReflectionStore());
    }

    /** 使用指定存储与默认规则式复盘。 */
    public ReflectionHook(ReflectionStore store) {
        this(store, ReflectionHook::reflect);
    }

    /** 使用指定存储与自定义复盘逻辑。 */
    public ReflectionHook(ReflectionStore store, Generator generator) {
        this.store = Objects.requireNonNull(store, "store 不能为空");
        this.generator = Objects.requireNonNull(generator, "generator 不能为空");
    }

    @Override
    public void onAgentEnd(Agent agent, AgentResponse response) {
        persist(agent, response, null);
    }

    @Override
    public void onError(Agent agent, Throwable error) {
        persist(agent, null, error);
    }

    private void persist(Agent agent, AgentResponse response, Throwable error) {
        try {
            Reflection reflection = generator.generate(agent, response, error);
            if (reflection != null) {
                store.save(reflection);
            }
        } catch (RuntimeException e) {
            // 观测组件不应影响主流程（Agent 层会兜底，这里再兜一层防止 store 抛异常）
            log.warn("ReflectionHook 复盘失败，已忽略: {}", e.toString());
        }
    }

    /** 底层经验存储。 */
    public ReflectionStore store() {
        return store;
    }

    /** 检索与给定任务相关的历史经验（最新在前）。 */
    public List<Reflection> recall(String task, int limit) {
        return store.search(task, limit);
    }

    /**
     * 把历史经验拼进系统提示词末尾，供下次任务参考。
     * 无经验时原样返回 {@code basePrompt}。
     */
    public static String augmentSystemPrompt(String basePrompt, List<Reflection> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return basePrompt;
        }
        StringBuilder sb = new StringBuilder(basePrompt == null ? "" : basePrompt);
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append("\n## 历史经验（供参考，可提升成功率）\n");
        for (Reflection r : experiences) {
            sb.append("- [").append(r.outcome()).append("] ").append(r.lesson());
            if (!r.task().isBlank()) {
                sb.append("（任务：").append(abbreviate(r.task(), 80)).append('）');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 复盘逻辑

    /** 复盘逻辑：一个函数式接口，可替换默认实现。 */
    @FunctionalInterface
    public interface Generator {
        /**
         * @param agent    触发复盘的 Agent
         * @param response 正常完成时的结果；异常时为 {@code null}
         * @param error    异常时的错误；正常完成时为 {@code null}
         * @return 生成的经验，返回 {@code null} 表示跳过本次复盘
         */
        Reflection generate(Agent agent, AgentResponse response, Throwable error);
    }

    /** 默认规则式复盘：从步骤与工具结果中提取结构化经验。 */
    static Reflection reflect(Agent agent, AgentResponse response, Throwable error) {
        String task = lastUserInput(agent);
        List<String> toolsUsed = toolNames(response, false);
        List<String> failedTools = toolNames(response, true);
        int iterations = response == null ? 0 : response.iterations();
        long duration = response == null ? 0 : response.durationMillis();

        Reflection.Outcome outcome = error != null
                ? Reflection.Outcome.FAILURE
                : (failedTools.isEmpty() ? Reflection.Outcome.SUCCESS : Reflection.Outcome.PARTIAL);

        String lesson = buildLesson(task, outcome, toolsUsed, failedTools, iterations, duration, error);
        return new Reflection(task, outcome, toolsUsed, failedTools, lesson, iterations, duration, null);
    }

    private static List<String> toolNames(AgentResponse response, boolean onlyFailed) {
        if (response == null) {
            return List.of();
        }
        return response.toolResults().stream()
                .filter(r -> !onlyFailed || r.error())
                .map(ToolResult::toolName)
                .distinct()
                .toList();
    }

    private static String lastUserInput(Agent agent) {
        List<Message> messages = agent.memory().messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == Role.USER) {
                String content = messages.get(i).content();
                return content == null ? "" : content;
            }
        }
        return "";
    }

    private static String buildLesson(String task, Reflection.Outcome outcome, List<String> toolsUsed,
                                      List<String> failedTools, int iterations, long duration, Throwable error) {
        String taskLabel = task.isBlank() ? "任务" : "任务「" + abbreviate(task, 60) + "」";
        return switch (outcome) {
            case SUCCESS -> taskLabel + "成功完成"
                    + (toolsUsed.isEmpty() ? "（未使用工具）" : "，使用工具 " + toolsUsed)
                    + "，共 " + iterations + " 轮，耗时 " + duration + "ms";
            case PARTIAL -> taskLabel + "完成，但工具 " + failedTools + " 执行失败，建议检查参数或改用替代工具";
            case FAILURE -> taskLabel + "执行失败："
                    + (error == null ? "未知原因" : abbreviate(String.valueOf(error.getMessage()), 120));
        };
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }
}
