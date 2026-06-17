package com.aitestforge.dto.recipe.response;

import com.aitestforge.domain.recipe.enums.RecipeValidationStatus;
import com.aitestforge.dto.recipe.ValidationIssue;

import java.util.List;

public record RecipeValidationResult(
        RecipeValidationStatus status,
        List<ValidationIssue> issues
) {}
