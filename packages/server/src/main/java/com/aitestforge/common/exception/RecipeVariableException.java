package com.aitestforge.common.exception;

import lombok.Getter;

/**
 * 레시피 변수 치환/추출 중 발생하는 예외.
 * - 미지원 gen 타입
 * - 미정의 변수 참조
 * - JSONPath 추출 실패
 */
@Getter
public class RecipeVariableException extends RuntimeException {

    private final String variableName;
    private final String responseBodyPreview; // null이면 생략

    public RecipeVariableException(String message, String variableName) {
        super(message);
        this.variableName = variableName;
        this.responseBodyPreview = null;
    }

    public RecipeVariableException(String message, String variableName, String responseBodyPreview) {
        super(message);
        this.variableName = variableName;
        this.responseBodyPreview = responseBodyPreview;
    }
}
