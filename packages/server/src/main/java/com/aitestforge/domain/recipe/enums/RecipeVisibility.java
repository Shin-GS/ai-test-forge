package com.aitestforge.domain.recipe.enums;

import com.aitestforge.domain.common.EnumColumn;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 레시피 공개 범위
 */
@Getter
@RequiredArgsConstructor
public enum RecipeVisibility implements EnumColumn {
    PUBLIC("PUBLIC", "전체 공개"),
    PRIVATE("PRIVATE", "비공개");

    private final String code;
    private final String description;
}
