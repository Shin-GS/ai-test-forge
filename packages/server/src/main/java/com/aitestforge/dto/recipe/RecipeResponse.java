package com.aitestforge.dto.recipe;

import com.aitestforge.domain.recipe.Recipe;

import java.time.LocalDateTime;
import java.util.List;

public record RecipeResponse(
        Long id,
        String name,
        String description,
        List<String> tags,
        String stepsJson,
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
                recipe.getUsageCount(),
                recipe.getCreatedAt(),
                recipe.getLastUsedAt()
        );
    }
}
