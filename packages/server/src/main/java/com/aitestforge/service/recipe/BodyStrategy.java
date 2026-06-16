package com.aitestforge.service.recipe;

/**
 * 레시피 step의 body 생성 전략.
 * step JSON의 "bodyStrategy" 필드로 지정하며, null이면 FIXED로 동작.
 */
public enum BodyStrategy {

    /** 고정 body — 변수 치환만 수행 (기존 로직) */
    FIXED,

    /** gen 변수 사용 — RecipeVariableResolver로 자동 생성 (기존 로직) */
    GEN,

    /** AI가 스키마 기반으로 전체 body를 생성 */
    AI_GENERATE,

    /** 기존 body를 1차 치환 후 null/{{auto}} 필드만 AI가 채움 */
    AI_FILL
}
