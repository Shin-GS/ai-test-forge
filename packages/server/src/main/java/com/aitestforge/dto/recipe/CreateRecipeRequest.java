package com.aitestforge.dto.recipe;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateRecipeRequest(
        @NotBlank String name,
        String description,
        List<String> tags,
        @NotBlank String stepsJson
) {
}
