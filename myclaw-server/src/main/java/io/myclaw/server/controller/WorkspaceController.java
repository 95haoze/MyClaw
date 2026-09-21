package io.myclaw.server.controller;

import io.myclaw.server.dto.ApiResponse;
import io.myclaw.server.service.WorkspacePathService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspacePathService service;

    public WorkspaceController(WorkspacePathService service) { this.service = service; }

    @GetMapping
    public ApiResponse<WorkspacePathService.DirectoryView> browse(@RequestParam(defaultValue = ".") String path) {
        return ApiResponse.ok(service.browse(path));
    }
    @PostMapping("/open")
    public ApiResponse<Void> open(@RequestBody OpenWorkspaceRequest request) {
        service.openInApplication(request.path(), request.application());
        return ApiResponse.ok(null);
    }
    @PostMapping("/select-directory")
    public ApiResponse<String> selectDirectory() {
        return ApiResponse.ok(service.selectDirectory());
    }

    public record OpenWorkspaceRequest(String path, String application) {}
}
