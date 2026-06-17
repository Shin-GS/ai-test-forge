package com.aitestforge.dto.spec.response;

public record ApiEndpointResponse(
        String method,
        String path,
        String summary,
        String tag
) {
}
