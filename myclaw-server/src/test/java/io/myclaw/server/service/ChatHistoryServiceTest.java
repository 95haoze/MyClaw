package io.myclaw.server.service;

import io.myclaw.core.message.Message;
import io.myclaw.server.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ChatHistoryServiceTest {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("test@example.com", "n/a", java.util.List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void truncatesPersistedConversationFromSelectedUserMessage() {
        String sessionId = "branch-test-session";
        chatHistoryService.beginRequest("branch-first", sessionId, "first", List.of());
        chatHistoryService.completeRequest("branch-first", sessionId,
                new ChatResponse("first answer", 1, 0, 4, 10, List.of(), null));
        Long secondUserId = chatHistoryService.beginRequest("branch-second", sessionId, "second", List.of());
        chatHistoryService.completeRequest("branch-second", sessionId,
                new ChatResponse("second answer", 1, 0, 4, 10, List.of(), null));

        chatHistoryService.truncateMessages(sessionId, secondUserId);

        var detail = chatHistoryService.getSession(sessionId);
        assertEquals(2, detail.messages().size());
        assertEquals("first", detail.messages().get(0).content());
        assertEquals("first answer", detail.messages().get(1).content());
    }

    @Test
    void completedMessagesAreLoadedAndCancelledMessagesAreExcluded() {
        String sessionId = "database-test-session";
        chatHistoryService.beginRequest("request-complete", sessionId, "hello", java.util.List.of());
        chatHistoryService.completeRequest(
                "request-complete",
                sessionId,
                new ChatResponse("hello back", 1, 0, 12, 80, java.util.List.of(), null)
        );
        chatHistoryService.beginRequest("request-cancel", sessionId, "unfinished question", java.util.List.of());
        chatHistoryService.cancelRequest(
                "request-cancel",
                sessionId,
                "partial answer"
        );

        List<Message> messages = chatHistoryService.loadRecentMessages(sessionId);

        assertEquals(2, messages.size());
        assertEquals("hello", messages.get(0).content());
        assertEquals("hello back", messages.get(1).content());

        chatHistoryService.deleteSession(sessionId);
        assertTrue(chatHistoryService.loadRecentMessages(sessionId).isEmpty());
    }

    @Test
    void toolExecutionsArePersistedAndReturnedWithSessionHistory() {
        String sessionId = "tool-history-session";
        chatHistoryService.beginRequest("tool-history-request", sessionId, "search", List.of());
        chatHistoryService.completeRequest(
                "tool-history-request",
                sessionId,
                new ChatResponse("result", 2, 1, 24, 150,
                        List.of(new ChatResponse.ToolExecution(
                                "call-1", "web_search", "{\"query\":\"Spring Boot\"}",
                                "completed", 42, "1. Spring Boot"
                        )), null)
        );

        var assistant = chatHistoryService.getSession(sessionId).messages().get(1);
        assertEquals(1, assistant.tools().size());
        var tool = assistant.tools().getFirst();
        assertEquals("call-1", tool.id());
        assertEquals("web_search", tool.name());
        assertEquals("completed", tool.status());
        assertEquals(42, tool.durationMillis());
        assertEquals("1. Spring Boot", tool.result());
    }}