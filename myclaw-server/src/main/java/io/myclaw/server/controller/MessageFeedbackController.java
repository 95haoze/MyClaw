package io.myclaw.server.controller;

import io.myclaw.server.dto.ApiResponse;
import io.myclaw.server.service.MessageFeedbackService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages/{messageId}/feedback")
public class MessageFeedbackController {
    private final MessageFeedbackService service;

    public MessageFeedbackController(MessageFeedbackService service) {
        this.service = service;
    }

    public record Request(String value) {
    }

    @PutMapping
    public ApiResponse<Void> save(@PathVariable Long messageId, @RequestBody Request request) {
        service.save(messageId, request.value());
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> delete(@PathVariable Long messageId) {
        service.delete(messageId);
        return ApiResponse.ok();
    }
}
