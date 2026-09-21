package com.chaosreplay.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standardized pagination envelope for API query endpoints.
 *
 * @param <T> element type contained within the paginated page
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PagedResponse<T> fromPage(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}

