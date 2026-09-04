package com.register.backend.dto.response;

import java.util.List;

/**
 * Generic paginated response envelope for list endpoints.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
