package com.aitestforge.domain.common;

/**
 * 모든 Enum 컬럼의 공통 인터페이스
 * - code: DB에 저장되는 값
 * - description: 한글 설명
 */
public interface EnumColumn {
    String getCode();
    String getDescription();
}
