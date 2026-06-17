package com.aitestforge.domain.recipe.enums;

import com.aitestforge.domain.common.EnumColumn;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 레시피 스펙 검증 결과 상태
 */
@Getter
@RequiredArgsConstructor
public enum RecipeValidationStatus implements EnumColumn {
    VALID("VALID", "유효"),
    WARN("WARN", "경고"),
    BROKEN("BROKEN", "실행 불가");

    private final String code;
    private final String description;
}
