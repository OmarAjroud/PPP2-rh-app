package com.hrapp.rh.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Pagination metadata and content returned by the API. */
public record PageReponse<T>(
        List<T> contenu,
        int numeroPage,
        int taillePage,
        long totalElements,
        int totalPages,
        boolean dernierePage
) {
    public static <T> PageReponse<T> from(Page<T> page) {
        return new PageReponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
