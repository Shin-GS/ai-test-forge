package com.aitestforge.dto.recipe.request;

import com.aitestforge.domain.recipe.enums.RecipeVisibility;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateRecipeRequest(
        @NotBlank String name,
        String description,
        List<String> tags,
        @NotBlank String stepsJson,
        RecipeVisibility visibility,
        String variablesJson
) {
}
