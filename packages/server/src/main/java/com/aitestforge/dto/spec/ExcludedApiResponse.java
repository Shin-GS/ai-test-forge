package com.aitestforge.dto.spec;

/**
 * 제외된 API 정보를 표현하는 DTO.
 * x-test-forge-exclude 또는 global-exclude에 의해 tool 목록에서 제외된 API.
 */
public record ExcludedApiResponse(
        String method,
        String path,
        String reason
) {
}
