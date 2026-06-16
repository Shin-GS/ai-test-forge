package com.aitestforge.dto.spec;

public record ApiEndpointResponse(
        String method,
        String path,
        String summary,
        String tag
) {
}
