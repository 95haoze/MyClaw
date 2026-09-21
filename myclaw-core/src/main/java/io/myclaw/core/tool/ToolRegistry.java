package io.myclaw.core.tool;

import io.myclaw.core.message.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 工具注册表：Agent 的"手"。
 *
 * <p>负责三件事：
 * <ol>
 *   <li>按名字查找工具，并把全部工具定义导出让模型感知</li>
 *   <li>执行模型请求的工具调用</li>
 *   <li><b>兜底容错</b> —— 任何工具抛出的异常、参数不合法、工具不存在，都会被转换成一条
 *       {@code ERROR: ...} 结果回填给模型，让模型自己纠错，而不是让整个 Agent 循环崩溃</li>
 * </ol>
 */
public final class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
    }

    /** 注册单个工具，返回自身以便链式调用。同名工具会被覆盖。 */
    public ToolRegistry register(Tool tool) {
        if (!tool.enabled()) {
            log.debug("工具 {} 处于禁用状态，跳过注册", tool.name());
            return this;
        }
        ToolDefinition definition = tool.definition();
        Tool previous = tools.put(definition.name(), tool);
        if (previous != null) {
            log.warn("工具名 {} 重复注册，后注册的实现将生效", definition.name());
        } else {
            log.debug("注册工具: {}", definition.name());
        }
        return this;
    }

    /** 批量注册。 */
    public ToolRegistry registerAll(Collection<? extends Tool> tools) {
        tools.forEach(this::register);
        return this;
    }

    /** 批量注册（可变参数）。 */
    public ToolRegistry registerAll(Tool... tools) {
        for (Tool tool : tools) {
            register(tool);
        }
        return this;
    }

    /** 注销一个工具。 */
    public boolean unregister(String name) {
        return tools.remove(name) != null;
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    public int size() {
        return tools.size();
    }

    /** 全部已注册工具名。 */
    public Set<String> names() {
        return Set.copyOf(tools.keySet());
    }

    /** 导出全部工具定义，交给模型。 */
    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    /** 创建只暴露符合策略的工具注册表。 */
    public ToolRegistry filtered(java.util.function.Predicate<String> allowed) {
        ToolRegistry filtered = new ToolRegistry();
        tools.forEach((name, tool) -> { if (allowed.test(name)) filtered.register(tool); });
        return filtered;
    }
    /** 执行一次工具调用，并将异常转换为失败结果。 */
    public ToolResult execute(ToolCall call, ToolContext context) {
        long startedAt = System.nanoTime();
        Tool tool = tools.get(call.name());
        if (tool == null) {
            long elapsed = elapsedMillis(startedAt);
            String message = "工具 `" + call.name() + "` 不存在。可用工具: " + tools.keySet();
            log.warn(message);
            return ToolResult.failure(call.id(), call.name(), message, elapsed);
        }
        try {
            String content = tool.call(io.myclaw.core.json.Json.parse(call.arguments()), context);
            long elapsed = elapsedMillis(startedAt);
            log.debug("工具 {} 执行成功，耗时 {}ms", call.name(), elapsed);
            return ToolResult.success(call.id(), call.name(), content == null ? "" : content, elapsed);
        } catch (Exception e) {
            long elapsed = elapsedMillis(startedAt);
            log.warn("工具 {} 执行失败: {}", call.name(), e.toString());
            return ToolResult.failure(call.id(), call.name(), describe(e), elapsed);
        }
    }

    /** 顺序执行一批工具调用。 */
    public List<ToolResult> executeAll(List<ToolCall> calls, ToolContext context) {
        List<ToolResult> results = new ArrayList<>(calls.size());
        for (ToolCall call : calls) {
            results.add(execute(call, context));
        }
        return results;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message;
    }
}
