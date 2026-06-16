package com.aitestforge.dto.recipe;

import com.aitestforge.domain.recipe.RecipeVisibility;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateRecipeRequest(
        @NotBlank String name,
        String description,
        List<String> tags,
        @NotBlank String stepsJson,
        RecipeVisibility visibility,
        String variablesJson
) {
}
