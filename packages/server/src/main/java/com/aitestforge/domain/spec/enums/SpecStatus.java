package com.aitestforge.domain.spec.enums;

import com.aitestforge.domain.common.EnumColumn;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * API 스펙 등록 상태
 *
 * 상태 전이:
 * - 동기 등록: → ACTIVE (즉시)
 * - 비동기 등록: → REGISTERING → 파싱 완료 → ACTIVE
 * - heartbeat 유지: ACTIVE
 * - heartbeat 미응답: → STALE
 * - REGISTERED: 예약 상태 (현재 미사용)
 */
@Getter
@RequiredArgsConstructor
public enum SpecStatus implements EnumColumn {
    REGISTERING("REGISTERING", "비동기 파싱 처리 중"),
    REGISTERED("REGISTERED", "정상 등록됨 (예약)"),
    ACTIVE("ACTIVE", "서버 동작 중"),
    STALE("STALE", "갱신되지 않음");

    private final String code;
    private final String description;
}
