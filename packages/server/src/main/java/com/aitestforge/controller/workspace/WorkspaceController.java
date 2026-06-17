package com.aitestforge.controller.workspace;

import com.aitestforge.domain.auth.User;
import com.aitestforge.dto.workspace.request.CreateWorkspaceRequest;
import com.aitestforge.dto.workspace.request.UpdateWorkspaceRequest;
import com.aitestforge.dto.workspace.response.WorkspaceResponse;
import com.aitestforge.service.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Workspace", description = "워크스페이스 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @Operation(summary = "워크스페이스 생성", description = "새 워크스페이스를 생성합니다.")
    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(workspaceService.create(request, user.getId()));
    }

    @Operation(summary = "워크스페이스 목록 조회", description = "현재 사용자의 워크스페이스 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getAll(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(workspaceService.getAll(user.getId()));
    }

    @Operation(summary = "워크스페이스 상세 조회", description = "특정 워크스페이스의 매핑 정보를 조회합니다.")
    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getById(
            @Parameter(description = "워크스페이스 ID") @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceService.getById(workspaceId));
    }

    @Operation(summary = "워크스페이스 수정", description = "워크스페이스 이름 및 매핑을 수정합니다.")
    @PutMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> update(
            @Parameter(description = "워크스페이스 ID") @PathVariable Long workspaceId,
            @RequestBody UpdateWorkspaceRequest request) {
        return ResponseEntity.ok(workspaceService.update(workspaceId, request));
    }

    @Operation(summary = "워크스페이스 삭제", description = "워크스페이스를 삭제합니다. 기본 워크스페이스는 삭제 불가.")
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "워크스페이스 ID") @PathVariable Long workspaceId) {
        workspaceService.delete(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
