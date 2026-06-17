package com.aitestforge.service.workspace;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.workspace.Workspace;
import com.aitestforge.domain.workspace.WorkspaceMapping;
import com.aitestforge.dto.workspace.WorkspaceMappingDto;
import com.aitestforge.dto.workspace.request.CreateWorkspaceRequest;
import com.aitestforge.dto.workspace.request.UpdateWorkspaceRequest;
import com.aitestforge.dto.workspace.response.WorkspaceResponse;import com.aitestforge.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Transactional
    public WorkspaceResponse create(CreateWorkspaceRequest request, Long userId) {
        // 첫 워크스페이스면 기본으로 설정
        boolean isFirst = workspaceRepository.findByUserIdOrderByCreatedAtAsc(userId).isEmpty();

        Workspace workspace = Workspace.builder()
                .userId(userId)
                .name(request.name())
                .isDefault(isFirst)
                .build();

        if (request.mappings() != null) {
            for (WorkspaceMappingDto dto : request.mappings()) {
                WorkspaceMapping mapping = WorkspaceMapping.builder()
                        .subdomainName(dto.subdomainName())
                        .environment(dto.environment())
                        .build();
                workspace.addMapping(mapping);
            }
        }

        workspaceRepository.save(workspace);
        log.info("Workspace created: {} for user {}", workspace.getName(), userId);
        return WorkspaceResponse.from(workspace);
    }

    public List<WorkspaceResponse> getAll(Long userId) {
        return workspaceRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(WorkspaceResponse::from)
                .toList();
    }

    public WorkspaceResponse getById(Long workspaceId) {
        Workspace workspace = findOrThrow(workspaceId);
        return WorkspaceResponse.from(workspace);
    }

    @Transactional
    public WorkspaceResponse update(Long workspaceId, UpdateWorkspaceRequest request) {
        Workspace workspace = findOrThrow(workspaceId);

        if (request.name() != null && !request.name().isBlank()) {
            workspace.updateName(request.name());
        }

        if (request.mappings() != null) {
            workspace.clearMappings();
            for (WorkspaceMappingDto dto : request.mappings()) {
                WorkspaceMapping mapping = WorkspaceMapping.builder()
                        .subdomainName(dto.subdomainName())
                        .environment(dto.environment())
                        .build();
                workspace.addMapping(mapping);
            }
        }

        log.info("Workspace updated: {}", workspace.getName());
        return WorkspaceResponse.from(workspace);
    }

    @Transactional
    public void delete(Long workspaceId) {
        Workspace workspace = findOrThrow(workspaceId);
        if (workspace.getIsDefault()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        workspaceRepository.delete(workspace);
        log.info("Workspace deleted: {}", workspace.getName());
    }

    /**
     * 사용자의 기본 워크스페이스의 매핑 목록을 반환한다.
     * Agent Loop에서 tool 필터링에 사용.
     */
    public List<WorkspaceMappingDto> getDefaultMappings(Long userId) {
        return workspaceRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(ws -> ws.getMappings().stream()
                        .map(WorkspaceMappingDto::from)
                        .toList())
                .orElse(List.of());
    }

    private Workspace findOrThrow(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
