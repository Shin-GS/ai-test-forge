package com.aitestforge.dto.workspace.request;

import com.aitestforge.dto.workspace.WorkspaceMappingDto;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateWorkspaceRequest(
        @NotBlank String name,
        List<WorkspaceMappingDto> mappings
) {
}
