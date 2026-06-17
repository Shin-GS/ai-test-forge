package com.aitestforge.dto.recipe.response;

import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.enums.RecipeValidationStatus;
import com.aitestforge.domain.recipe.enums.RecipeVisibility;

import java.time.LocalDateTime;
import java.util.List;

public record RecipeResponse(
        Long id,
        String name,
        String description,
        List<String> tags,
        String stepsJson,
        RecipeVisibility visibility,
        String variablesJson,
        RecipeValidationStatus validationStatus,
        String validationMessage,
        Integer usageCount,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt
) {
    public static RecipeResponse from(Recipe recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getDescription(),
                recipe.getTags(),
                recipe.getStepsJson(),
                recipe.getVisibility(),
                recipe.getVariablesJson(),
                recipe.getValidationStatus(),
                recipe.getValidationMessage(),
                recipe.getUsageCount(),
                recipe.getCreatedAt(),
                recipe.getLastUsedAt()
        );
    }
}
