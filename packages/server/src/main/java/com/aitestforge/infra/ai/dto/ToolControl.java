package com.aitestforge.infra.ai.dto;

import java.util.List;

/**
 * API에 적용된 제어 메타데이터.
 * x-test-forge-* 확장 필드에서 파싱한 결과.
 */
public record ToolControl(
        boolean blocked,
        String blockReason,
        String confirmMessage,   // null이면 confirm 없음
        boolean readonly,
        List<String> groups
) {
    public static ToolControl none() {
        return new ToolControl(false, null, null, false, List.of());
    }
}
