package com.aitestforge.dto.workspace;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateWorkspaceRequest(
        @NotBlank String name,
        List<WorkspaceMappingDto> mappings
) {
}
