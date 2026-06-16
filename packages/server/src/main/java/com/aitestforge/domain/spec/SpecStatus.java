package com.aitestforge.domain.spec;

/**
 * API 스펙 등록 상태.
 *
 * 상태 전이:
 * - 동기 등록: → ACTIVE (즉시)
 * - 비동기 등록: → REGISTERING → 파싱 완료 → ACTIVE
 * - heartbeat 유지: ACTIVE
 * - heartbeat 미응답: → STALE
 * - REGISTERED: 예약 상태 (현재 미사용)
 */
public enum SpecStatus {
    REGISTERING,
    /** 정상 등록됨, heartbeat 전 초기 상태 */
    REGISTERED,
    ACTIVE,
    STALE
}
