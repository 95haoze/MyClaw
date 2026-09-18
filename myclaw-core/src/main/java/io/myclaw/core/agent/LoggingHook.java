package io.myclaw.core.agent;

import io.myclaw.core.message.ToolCall;
import io.myclaw.core.model.ChatRequest;
import io.myclaw.core.model.ChatResponse;
import io.myclaw.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 Agent 的关键动作打到日志里的钩子，开箱即用。
 *
 * <p>模型调用与工具调用打到 DEBUG，异常与最终统计打到 WARN / INFO，
 * 因此生产环境只开 INFO 也不会刷屏。
 */
public class LoggingHook implements AgentHook {

    private static final Logger log = LoggerFactory.getLogger(LoggingHook.class);

    private final boolean logPayload;

    public LoggingHook() {
        this(false);
    }

    /**
     * @param logPayload 是否把完整的请求/响应体也打出来（排查提示词问题时很有用，但日志量很大）
     */
    public LoggingHook(boolean logPayload) {
        this.logPayload = logPayload;
    }

    @Override
    public void onAgentStart(Agent agent, AgentRequest request) {
        log.debug("[{}] 开始处理: {}", agent.name(), abbreviate(request.input(), 200));
    }

    @Override
    public void onModelRequest(Agent agent, ChatRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] 调用模型: {} 条消息, {} 个工具, options={}",
                    agent.name(), request.messages().size(), request.tools().size(), request.options().model());
            if (logPayload) {
                request.messages().forEach(m -> log.debug("    {}", m));
            }
        }
    }

    @Override
    public void onModelResponse(Agent agent, ChatResponse response) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] 模型返回: finishReason={}, usage=[{}], toolCalls={}",
                    agent.name(), response.finishReason(), response.usage(),
                    response.toolCalls().stream().map(ToolCall::name).toList());
        }
    }

    @Override
    public void onToolCall(Agent agent, ToolCall call, ToolResult result) {
        if (result.error()) {
            log.warn("[{}] 工具 {} 失败: {}", agent.name(), call.name(), abbreviate(result.content(), 300));
        } else {
            log.debug("[{}] 工具 {} 成功 ({}ms): {}",
                    agent.name(), call.name(), result.durationMillis(), abbreviate(result.content(), 300));
        }
    }

    @Override
    public void onAgentEnd(Agent agent, AgentResponse response) {
        log.debug("[{}] 完成: {}", agent.name(), response);
    }

    @Override
    public void onError(Agent agent, Throwable error) {
        log.warn("[{}] 执行失败: {}", agent.name(), error.toString());
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }
}
