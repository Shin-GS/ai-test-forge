package com.aitestforge.dto.recipe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateRecipeFromSessionRequest(
        @NotNull Long sessionId,
        int[] stepRange,  // [startIndex, endIndex] (null이면 전체)
        @NotBlank String name,
        String description,
        List<String> tags
) {
}
