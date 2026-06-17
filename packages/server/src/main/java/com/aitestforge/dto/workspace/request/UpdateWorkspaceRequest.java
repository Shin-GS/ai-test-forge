package com.aitestforge.dto.workspace.request;

import com.aitestforge.dto.workspace.WorkspaceMappingDto;

import java.util.List;

public record UpdateWorkspaceRequest(
        String name,
        List<WorkspaceMappingDto> mappings
) {
}
