package com.aitestforge.dto.workspace.response;

import com.aitestforge.domain.workspace.Workspace;
import com.aitestforge.dto.workspace.WorkspaceMappingDto;

import java.time.LocalDateTime;
import java.util.List;

public record WorkspaceResponse(
        Long id,
        String name,
        Boolean isDefault,
        List<WorkspaceMappingDto> mappings,
        LocalDateTime createdAt
) {
    public static WorkspaceResponse from(Workspace workspace) {
        List<WorkspaceMappingDto> mappingDtos = workspace.getMappings().stream()
                .map(WorkspaceMappingDto::from)
                .toList();
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getIsDefault(),
                mappingDtos,
                workspace.getCreatedAt()
        );
    }
}
