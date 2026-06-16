package com.aitestforge.service.recipe;

/**
 * 레시피 step의 결과 선택 전략.
 * step JSON의 "selectStrategy" 필드로 지정하며, null이면 무시.
 */
public enum SelectStrategy {

    /** GET 결과 목록에서 AI가 aiHint 조건에 맞는 항목을 선택 */
    AI_PICK
}
