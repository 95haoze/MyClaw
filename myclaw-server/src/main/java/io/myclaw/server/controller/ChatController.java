package io.myclaw.server.controller;

import io.myclaw.server.annotation.RepeatSubmit;
import io.myclaw.server.dto.*;
import io.myclaw.server.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chatService;
    private final StreamingChatService streamingChatService;
    private final ChatHistoryService chatHistoryService;
    private final AgentSessionManager agentSessionManager;
    public ChatController(ChatService chatService, StreamingChatService streamingChatService,
                          ChatHistoryService chatHistoryService, AgentSessionManager agentSessionManager) {
        this.chatService = chatService; this.streamingChatService = streamingChatService;
        this.chatHistoryService = chatHistoryService;
        this.agentSessionManager = agentSessionManager;
    }

    @RepeatSubmit(interval = 1000)
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.chat(request));
    }

    @RepeatSubmit(interval = 1000)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return streamingChatService.stream(request);
    }

    @DeleteMapping("/requests/{requestId}")
    public ApiResponse<Void> cancelRequest(@PathVariable String requestId) {
        streamingChatService.cancel(requestId); return ApiResponse.ok();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> resetSession(@PathVariable String sessionId) {
        chatService.reset(sessionId); return ApiResponse.ok();
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionDtos.SessionSummary>> listSessions() {
        return ApiResponse.ok(chatHistoryService.listSessions());
    }

    @RepeatSubmit(interval = 1000)
    @PostMapping("/sessions")
    public ApiResponse<SessionDtos.SessionSummary> createSession(
            @RequestBody(required = false) SessionDtos.CreateSession request) {
        return ApiResponse.ok(chatHistoryService.createSession(
                request == null ? null : request.id(), request == null ? null : request.title()));
    }

    @RepeatSubmit(interval = 1000)
    @DeleteMapping("/sessions/{sessionId}/messages/{fromMessageId}")
    public ApiResponse<Void> truncateMessages(@PathVariable String sessionId, @PathVariable Long fromMessageId) {
        chatHistoryService.truncateMessages(sessionId, fromMessageId);
        agentSessionManager.remove(sessionId);
        return ApiResponse.ok();
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionDtos.SessionDetail> getSession(@PathVariable String sessionId) {
        return ApiResponse.ok(chatHistoryService.getSession(sessionId));
    }

    @RepeatSubmit(interval = 1000)
    @PatchMapping("/sessions/{sessionId}")
    public ApiResponse<Void> renameSession(@PathVariable String sessionId,
                                           @RequestBody SessionDtos.RenameSession request) {
        chatHistoryService.renameSession(sessionId, request.title()); return ApiResponse.ok();
    }

    @RepeatSubmit(interval = 1000)
    @DeleteMapping("/sessions/{sessionId}/messages")
    public ApiResponse<Void> clearSession(@PathVariable String sessionId) {
        chatService.clear(sessionId); return ApiResponse.ok();
    }
}
