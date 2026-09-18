package io.myclaw.core.agent;

import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.tool.ToolResult;

/**
 * Agent 生命周期钩子 —— 可观测性与横切逻辑的插入点。
 *
 * <p>所有方法都有空实现，按需覆盖即可。钩子中抛出的异常会被框架捕获并记录，
 * <b>不会</b>中断 Agent 循环（观测组件不应该影响主流程）。
 *
 * @see LoggingHook
 */
public interface AgentHook {

    /** Agent 开始处理一次请求（此时用户消息已写入记忆）。 */
    default void onAgentStart(Agent agent, AgentRequest request) {
    }

    /** 即将发起一次模型调用（可能一轮内调用多次）。 */
    default void onModelRequest(Agent agent, ChatRequest request) {
    }

    /** 一次模型调用返回。 */
    default void onModelResponse(Agent agent, ChatResponse response) {
    }

    /** 一次工具调用执行完毕（含失败）。 */
    default void onToolCall(Agent agent, ToolCall call, ToolResult result) {
    }

    /** Agent 正常产出最终答案。 */
    default void onAgentEnd(Agent agent, AgentResponse response) {
    }

    /** Agent 执行过程中出现异常。 */
    default void onError(Agent agent, Throwable error) {
    }
}
