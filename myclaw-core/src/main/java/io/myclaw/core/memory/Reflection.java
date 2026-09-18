package io.myclaw.core.memory;

import java.time.Instant;
import java.util.List;

/**
 * 一条复盘经验 —— Agent 的"长期记忆"。
 *
 * <p>与 {@link Memory}（短期对话记忆）不同，{@code Reflection} 记录的是<b>跨会话</b>的
 * 经验沉淀：某个任务成功了还是失败了、用了哪些工具、踩了什么坑。这些经验可以在下次
 * 遇到类似任务时被检索并注入提示词，让 Agent 越用越顺手。
 *
 * @param task          任务摘要（通常是用户输入，取尾部一条 USER 消息）
 * @param outcome       结果：成功 / 部分成功 / 失败
 * @param toolsUsed     本次实际使用过的工具名（去重）
 * @param failedTools   失败的工具名（去重）
 * @param lesson        经验/教训的自然语言描述
 * @param iterations    经历的模型调用轮数
 * @param durationMillis 总耗时（毫秒）
 * @param createdAt     产生时间
 */
public record Reflection(
        String task,
        Outcome outcome,
        List<String> toolsUsed,
        List<String> failedTools,
        String lesson,
        int iterations,
        long durationMillis,
        Instant createdAt) {

    public enum Outcome { SUCCESS, PARTIAL, FAILURE }

    public Reflection {
        task = task == null ? "" : task;
        outcome = outcome == null ? Outcome.SUCCESS : outcome;
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
        failedTools = failedTools == null ? List.of() : List.copyOf(failedTools);
        lesson = lesson == null ? "" : lesson;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** 本次任务是否完全成功。 */
    public boolean successful() {
        return outcome == Outcome.SUCCESS;
    }
}
