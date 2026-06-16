package com.aitestforge.dto.recipe;

import com.aitestforge.domain.recipe.RecipeValidationStatus;

import java.util.List;

public record RecipeValidationResult(
        RecipeValidationStatus status,
        List<ValidationIssue> issues
) {}
