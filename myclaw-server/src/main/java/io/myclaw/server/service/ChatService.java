package io.myclaw.server.service;

import io.myclaw.server.dto.ChatMessage;
import io.myclaw.server.dto.ChatRequest;
import io.myclaw.server.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final AgentSessionManager agentSessionManager;
    private final ChatHistoryService chatHistoryService;
    private final AttachmentService attachmentService;

    public ChatService(
            AgentSessionManager agentSessionManager,
            ChatHistoryService chatHistoryService, AttachmentService attachmentService
    ) {
        this.agentSessionManager = agentSessionManager;
        this.chatHistoryService = chatHistoryService;
        this.attachmentService = attachmentService;
    }

    public ChatResponse chat(ChatRequest request) {
        ChatMessage userMessage = findLastUserMessage(request);
        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString()
                : request.requestId().strip();
        String sessionId = requireSessionId(request);
        String content = userMessage.content().strip();

        chatHistoryService.beginRequest(requestId, sessionId, content, request.attachmentIds());
        try {
            ChatResponse response = agentSessionManager.execute(
                    sessionId,
                    agent -> ChatResponse.from(agent.run(content + attachmentService.context(sessionId, request.attachmentIds())))
            );
            Long messageId = chatHistoryService.completeRequest(requestId, sessionId, response);
            return response.withMessageId(messageId);
        } catch (RuntimeException exception) {
            try {
                chatHistoryService.failRequest(requestId, sessionId, "");
            } catch (RuntimeException persistenceException) {
                exception.addSuppressed(persistenceException);
                log.error("请求失败状态保存失败，请求编号={}", requestId, persistenceException);
            } finally {
                agentSessionManager.remove(sessionId);
            }
            throw exception;
        }
    }

    public void reset(String sessionId) {
        agentSessionManager.remove(sessionId);
        chatHistoryService.deleteSession(sessionId.strip());
    }

    public void clear(String sessionId) {
        agentSessionManager.remove(sessionId);
        chatHistoryService.clearSession(sessionId.strip());
    }

    static String requireSessionId(ChatRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        return request.sessionId().strip();
    }

    static ChatMessage findLastUserMessage(ChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }

        ChatMessage userMessage = request.messages().reversed().stream()
                .filter(message -> message != null && "user".equalsIgnoreCase(message.role()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("messages 中至少需要一条 user 消息"));

        if (userMessage.content() == null || userMessage.content().isBlank()) {
            throw new IllegalArgumentException("用户消息内容不能为空");
        }
        return userMessage;
    }
}
