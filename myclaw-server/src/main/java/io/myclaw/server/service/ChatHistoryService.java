package io.myclaw.server.service;

import io.myclaw.core.message.Message;
import io.myclaw.server.dto.ChatResponse;
import io.myclaw.server.dto.SessionDtos;
import io.myclaw.server.persistence.entity.ChatMessageEntity;
import io.myclaw.server.persistence.entity.ChatSessionEntity;
import io.myclaw.server.persistence.entity.ChatToolExecutionEntity;
import io.myclaw.server.persistence.repository.ChatMessageRepository;
import io.myclaw.server.persistence.repository.ChatSessionRepository;
import io.myclaw.server.persistence.repository.ChatToolExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ChatHistoryService {

    private static final String USER = "user";
    private static final String ASSISTANT = "assistant";
    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String CANCELLED = "CANCELLED";

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AttachmentService attachmentService;
    private final MessageFeedbackService feedbackService;
    private final ChatToolExecutionRepository toolExecutionRepository;

    public ChatHistoryService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            AttachmentService attachmentService,
            MessageFeedbackService feedbackService,
            ChatToolExecutionRepository toolExecutionRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.attachmentService = attachmentService;
        this.feedbackService = feedbackService;
        this.toolExecutionRepository = toolExecutionRepository;
    }

    @Transactional
    public Long beginRequest(String requestId, String sessionId, String content, List<String> attachmentIds) {
        if (messageRepository.existsByRequestIdAndRole(requestId, USER)) {
            throw new IllegalArgumentException("requestId 已经使用过");
        }

        ChatSessionEntity session = sessionRepository.findByIdAndOwnerEmail(sessionId, owner())
                .orElseGet(() -> new ChatSessionEntity(sessionId, createTitle(content), owner()));
        session.touch();
        sessionRepository.save(session);
        ChatMessageEntity userMessage = messageRepository.save(new ChatMessageEntity(requestId, sessionId, USER, content, PENDING));
        attachmentService.linkToMessage(sessionId, userMessage.getId(), attachmentIds);
        return userMessage.getId();
    }

    @Transactional
    public Long completeRequest(String requestId, String sessionId, ChatResponse response) {
        ChatMessageEntity completedUser = userMessage(requestId);
        completedUser.markStatus(COMPLETED);
        messageRepository.save(completedUser);

        ChatMessageEntity assistant = new ChatMessageEntity(
                requestId, sessionId, ASSISTANT, response.content(), COMPLETED
        );
        assistant.setStatistics(
                response.iterations(),
                response.toolCalls(),
                response.totalTokens(),
                response.durationMillis()
        );
        assistant = messageRepository.save(assistant);
        List<ChatToolExecutionEntity> executions = new ArrayList<>();
        for (int index = 0; index < response.tools().size(); index++) {
            ChatResponse.ToolExecution tool = response.tools().get(index);
            executions.add(new ChatToolExecutionEntity(
                    assistant.getId(), tool.id(), tool.name(), tool.arguments(), tool.status(),
                    tool.durationMillis(), tool.result(), index
            ));
        }
        toolExecutionRepository.saveAll(executions);
        sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).ifPresent(session -> {
            session.touch();
            sessionRepository.save(session);
        });
        return assistant.getId();
    }

    @Transactional
    public void cancelRequest(String requestId, String sessionId, String partialContent) {
        ChatMessageEntity cancelledUser = userMessage(requestId);
        cancelledUser.markStatus(CANCELLED);
        messageRepository.save(cancelledUser);
        savePartialAssistant(requestId, sessionId, partialContent, CANCELLED);
    }

    @Transactional
    public void failRequest(String requestId, String sessionId, String partialContent) {
        ChatMessageEntity failedUser = userMessage(requestId);
        failedUser.markStatus(FAILED);
        messageRepository.save(failedUser);
        savePartialAssistant(requestId, sessionId, partialContent, FAILED);
    }

    @Transactional(readOnly = true)
    public List<Message> loadRecentMessages(String sessionId) {
        List<ChatMessageEntity> stored = new ArrayList<>(
                messageRepository.findTop40BySessionIdAndStatusesOrderByCreatedAtDesc(
                        sessionId, List.of(COMPLETED, FAILED, CANCELLED)
                )
        );
        Collections.reverse(stored);
        return stored.stream().map(this::toCoreMessage).toList();
    }

    @Transactional
    public void deleteSession(String sessionId) {
        ChatSessionEntity session = sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "会话不存在"));
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
    }

    @Transactional
    public void truncateMessages(String sessionId, Long fromMessageId) {

        if (fromMessageId == null) throw new IllegalArgumentException("缺少起始消息 ID");

        if (sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "会话不存在");

        List<Long> ids = messageRepository.findIdsFrom(sessionId, fromMessageId);

        if (ids.isEmpty() || !ids.contains(fromMessageId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "消息不存在");

        attachmentService.deleteForMessages(ids);
        feedbackService.deleteForMessages(ids);
        messageRepository.deleteByIds(ids);
        sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).ifPresent(session -> {
            session.touch();
            sessionRepository.save(session);
        });
    }

    @Transactional(readOnly = true)
    public List<SessionDtos.SessionSummary> listSessions() {
        return sessionRepository.findAllByOwnerEmailOrderByUpdatedAtDesc(owner()).stream()
                .map(session -> new SessionDtos.SessionSummary(session.getId(), session.getTitle(), session.getCreatedAt(),
                        session.getUpdatedAt(), messageRepository.countBySessionId(session.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionDtos.SessionDetail getSession(String sessionId) {
        ChatSessionEntity session = sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "会话不存在"));
        return new SessionDtos.SessionDetail(session.getId(), session.getTitle(), session.getCreatedAt(), session.getUpdatedAt(),
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream().map(this::toStoredMessage).toList());
    }

    @Transactional
    public SessionDtos.SessionSummary createSession(String requestedId, String requestedTitle) {
        String id = requestedId == null || requestedId.isBlank() ? UUID.randomUUID().toString() : requestedId.strip();
        if (id.length() > 64 || sessionRepository.findByIdAndOwnerEmail(id, owner()).isPresent())
            throw new IllegalArgumentException("会话 ID 无效或已存在");
        ChatSessionEntity session = sessionRepository.save(new ChatSessionEntity(id, normalizeTitle(requestedTitle, "新对话"), owner()));
        return new SessionDtos.SessionSummary(id, session.getTitle(), session.getCreatedAt(), session.getUpdatedAt(), 0);
    }

    @Transactional
    public void renameSession(String sessionId, String title) {
        ChatSessionEntity session = sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "会话不存在"));
        session.rename(normalizeTitle(title, null));
        sessionRepository.save(session);
    }

    @Transactional
    public void clearSession(String sessionId) {
        if (sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "会话不存在");
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.findByIdAndOwnerEmail(sessionId, owner()).ifPresent(session -> {
            session.touch();
            sessionRepository.save(session);
        });
    }

    private SessionDtos.StoredMessage toStoredMessage(ChatMessageEntity message) {
        return new SessionDtos.StoredMessage(message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                message.getCreatedAt(), message.getIterations(), message.getToolCalls(), message.getTotalTokens(), message.getDurationMillis(),
                toolExecutionRepository.findByMessageId(message.getId()).stream().map(tool -> new ChatResponse.ToolExecution(
                        tool.getExecutionId(), tool.getToolName(), tool.getArguments(), tool.getStatus(),
                        tool.getDurationMillis(), tool.getResult()
                )).toList(),
                attachmentService.viewsForMessage(message.getId()), feedbackService.value(message.getId()));
    }

    private static String normalizeTitle(String title, String fallback) {
        String value = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        if (value.isBlank()) {
            if (fallback != null) return fallback;
            throw new IllegalArgumentException("标题不能为空");
        }
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private ChatMessageEntity userMessage(String requestId) {
        return messageRepository.findFirstByRequestIdAndRole(requestId, USER)
                .orElseThrow(() -> new IllegalStateException("找不到请求对应的用户消息"));
    }

    private void savePartialAssistant(
            String requestId, String sessionId, String content, String status
    ) {
        if (content != null && !content.isBlank()) {
            messageRepository.save(new ChatMessageEntity(
                    requestId, sessionId, ASSISTANT, content, status
            ));
        }
    }

    private Message toCoreMessage(ChatMessageEntity message) {
        return switch (message.getRole()) {
            case USER -> Message.user(message.getContent());
            case ASSISTANT -> Message.assistant(message.getContent());
            default -> throw new IllegalStateException("不支持的消息角色: " + message.getRole());
        };
    }

    private static String createTitle(String content) {
        String title = content.strip().replaceAll("\\s+", " ");
        return title.length() <= 80 ? title : title.substring(0, 80);
    }

    private static String owner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new org.springframework.security.access.AccessDeniedException("未登录");
        return auth.getName();
    }
}
