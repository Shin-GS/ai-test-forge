package com.aitestforge.dto.recipe;

import java.util.Map;

/**
 * 레시피 실행 요청.
 * variables: {{input:라벨}} 변수에 대한 사용자 입력 값.
 * 예: {"포지션ID": "123"}
 */
public record ExecuteRecipeRequest(
        Map<String, String> variables
) {
    public ExecuteRecipeRequest {
        if (variables == null) {
            variables = Map.of();
        }
    }
}
