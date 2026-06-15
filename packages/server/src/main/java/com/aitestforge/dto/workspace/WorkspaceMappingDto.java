package com.aitestforge.dto.workspace;

import com.aitestforge.domain.workspace.WorkspaceMapping;
import jakarta.validation.constraints.NotBlank;

public record WorkspaceMappingDto(
        @NotBlank String subdomainName,
        @NotBlank String environment
) {
    public static WorkspaceMappingDto from(WorkspaceMapping mapping) {
        return new WorkspaceMappingDto(mapping.getSubdomainName(), mapping.getEnvironment());
    }
}
