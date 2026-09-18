package io.myclaw.server.controller;

import io.myclaw.server.dto.ApiResponse;
import io.myclaw.server.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {
    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentService.View> upload(@RequestParam String sessionId, @RequestPart MultipartFile file) throws IOException {
        return ApiResponse.ok(service.upload(sessionId, file));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable String id) {
        var attachment = service.require(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"attachment\"").body(service.content(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) throws IOException {
        service.delete(id);
        return ApiResponse.ok();
    }
}
