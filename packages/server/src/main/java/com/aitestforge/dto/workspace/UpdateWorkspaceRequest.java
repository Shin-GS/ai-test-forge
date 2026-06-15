package com.aitestforge.dto.workspace;

import java.util.List;

public record UpdateWorkspaceRequest(
        String name,
        List<WorkspaceMappingDto> mappings
) {
}
