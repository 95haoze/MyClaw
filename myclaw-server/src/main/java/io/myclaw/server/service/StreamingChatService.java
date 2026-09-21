package io.myclaw.server.service;

import io.myclaw.core.agent.AgentResponse;
import io.myclaw.core.model.StreamListener;
import io.myclaw.core.message.ToolCall;
import io.myclaw.core.tool.ToolResult;
import io.myclaw.server.dto.ChatMessage;
import io.myclaw.server.dto.ChatRequest;
import io.myclaw.server.dto.ChatResponse;
import io.myclaw.server.error.ApiErrorDescriptor;
import io.myclaw.server.stream.ChatStreamEvents;
import io.myclaw.server.stream.RunningRequest;
import io.myclaw.server.stream.RunningRequestManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

@Service
public class StreamingChatService {

    private static final Logger log = LoggerFactory.getLogger(StreamingChatService.class);

    private static final long SSE_TIMEOUT_MILLIS = 600_000L;

    private final AsyncTaskExecutor streamingExecutor;
    private final RunningRequestManager runningRequestManager;
    private final AgentSessionManager agentSessionManager;
    private final ChatHistoryService chatHistoryService;
    private final AttachmentService attachmentService;
    private final WorkspacePathService workspacePathService;

    public StreamingChatService(
            @Qualifier("streamingExecutor") AsyncTaskExecutor streamingExecutor,
            RunningRequestManager runningRequestManager,
            AgentSessionManager agentSessionManager,
            ChatHistoryService chatHistoryService, AttachmentService attachmentService,
            WorkspacePathService workspacePathService
    ) {
        this.streamingExecutor = streamingExecutor;
        this.runningRequestManager = runningRequestManager;
        this.agentSessionManager = agentSessionManager;
        this.chatHistoryService = chatHistoryService;
        this.attachmentService = attachmentService;
        this.workspacePathService = workspacePathService;
    }

    public SseEmitter stream(ChatRequest request) {
        ChatMessage userMessage = ChatService.findLastUserMessage(request);
        String requestId = requireRequestId(request);
        String sessionId = ChatService.requireSessionId(request);
        String content = userMessage.content().strip();

        RunningRequest runningRequest = runningRequestManager.start(requestId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitter.onTimeout(() -> runningRequestManager.cancel(requestId));
        emitter.onError(error -> runningRequestManager.cancel(requestId));
        emitter.onCompletion(() -> runningRequestManager.cancel(requestId));

        try {
            Future<?> future = streamingExecutor.submit(() -> execute(
                    requestId, sessionId, content, request.attachmentIds(), request.workingDirectory(), request.permissionMode(), runningRequest, emitter
            ));
            runningRequest.attachFuture(future);
        } catch (RejectedExecutionException exception) {
            runningRequestManager.complete(requestId, runningRequest);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "当前请求过多，请稍后重试",
                    exception
            );
        }

        return emitter;
    }

    public boolean cancel(String requestId) {
        return runningRequestManager.cancel(requestId);
    }

    private void execute(
            String requestId,
            String sessionId,
            String content,
            java.util.List<String> attachmentIds,
            String workingDirectory,
            String permissionMode,
            RunningRequest runningRequest,
            SseEmitter emitter
    ) {
        StringBuilder partialContent = new StringBuilder();
        boolean began = false;
        boolean completed = false;

        try {
            runningRequest.checkCancelled();
            Long userMessageId = chatHistoryService.beginRequest(requestId, sessionId, content, attachmentIds);
            began = true;
            send(emitter, runningRequest, "start", new ChatStreamEvents.Start(requestId, userMessageId));

            AgentResponse agentResponse = agentSessionManager.execute(
                    sessionId, workspacePathService.resolve(workingDirectory), permissionMode,
                    agent -> agent.run(content + attachmentService.context(sessionId, attachmentIds), listener(runningRequest, emitter, partialContent))
            );
            ChatResponse response = ChatResponse.from(agentResponse);
            response = response.withMessageId(chatHistoryService.completeRequest(requestId, sessionId, response));
            completed = true;

            send(emitter, runningRequest, "complete", response);
            runningRequestManager.complete(requestId, runningRequest);
            emitter.complete();
        } catch (CancellationException exception) {
            if (began && !completed) {
                try {
                    chatHistoryService.cancelRequest(requestId, sessionId, partialContent.toString());
                } catch (RuntimeException persistenceException) {
                    log.warn("请求取消状态保存失败，请求编号={}", requestId, persistenceException);
                } finally {
                    agentSessionManager.remove(sessionId);
                }
            }
            runningRequestManager.complete(requestId, runningRequest);
            emitter.complete();
        } catch (RuntimeException exception) {
            if (began && !completed) {
                try {
                    chatHistoryService.failRequest(requestId, sessionId, partialContent.toString());
                } catch (RuntimeException persistenceException) {
                    exception.addSuppressed(persistenceException);
                    log.warn("请求失败状态保存失败，请求编号={}", requestId, persistenceException);
                } finally {
                    agentSessionManager.remove(sessionId);
                }
            }
            try {
                ApiErrorDescriptor descriptor = ApiErrorDescriptor.from(exception);
                send(emitter, runningRequest, "error", new ChatStreamEvents.Error(
                        descriptor.code(), descriptor.message(), descriptor.retryable()
                ));
            } catch (CancellationException ignored) {
                // 客户端已经断开，不再发送错误事件。
            }
            runningRequestManager.complete(requestId, runningRequest);
            emitter.complete();
        } finally {
            runningRequestManager.complete(requestId, runningRequest);
            emitter.complete();
        }
    }

    private StreamListener listener(
            RunningRequest runningRequest,
            SseEmitter emitter,
            StringBuilder partialContent
    ) {
        return new StreamListener() {
            @Override
            public void onCancellable(Runnable cancelAction) {
                runningRequest.attachTransportCancel(cancelAction);
            }

            @Override
            public boolean isCancelled() {
                return runningRequest.isCancelled();
            }

            @Override
            public void onModelRequest(int iteration) {
                send(emitter, runningRequest, "status", new ChatStreamEvents.Status(
                        "model_request",
                        iteration == 1 ? "正在分析请求" : "正在根据工具结果继续处理",
                        iteration
                ));
            }

            @Override
            public void onToolsStart(int iteration, java.util.List<ToolCall> calls) {
                for (ToolCall call : calls) {
                    send(emitter, runningRequest, "tool_start", new ChatStreamEvents.Tool(
                            call.id(), call.name(), call.arguments(), "running", 0, ""
                    ));
                }
            }

            @Override
            public void onToolComplete(int iteration, ToolCall call, ToolResult result) {
                send(emitter, runningRequest, "tool_complete", new ChatStreamEvents.Tool(
                        call.id(), call.name(), call.arguments(),
                        result.error() ? "failed" : "completed",
                        result.durationMillis(), result.content()
                ));
            }
            @Override
            public void onText(String delta) {
                runningRequest.checkCancelled();
                partialContent.append(delta);
                send(emitter, runningRequest, "delta", new ChatStreamEvents.Delta(delta));
            }
        };
    }

    private static void send(
            SseEmitter emitter, RunningRequest runningRequest, String event, Object data
    ) {
        runningRequest.checkCancelled();
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException exception) {
            runningRequest.cancel();
            throw new CancellationException("SSE 连接已经断开");
        }
    }

    private static String requireRequestId(ChatRequest request) {
        if (request == null || request.requestId() == null || request.requestId().isBlank()) {
            throw new IllegalArgumentException("流式请求必须提供 requestId");
        }
        String requestId = request.requestId().strip();
        if (requestId.length() > 64) {
            throw new IllegalArgumentException("requestId 长度不能超过 64");
        }
        return requestId;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "流式请求执行失败" : message;
    }
}
