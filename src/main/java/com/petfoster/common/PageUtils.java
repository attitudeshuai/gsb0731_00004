package com.petfoster.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class PageUtils {

    private PageUtils() {
    }

    public static Pageable buildPageable(int page, int size, String sort,
                                          String defaultSortField,
                                          Set<String> allowedSortFields) {
        return PageRequest.of(page, size, parseSort(sort, defaultSortField, allowedSortFields));
    }

    public static Sort parseSort(String sort, String defaultSortField, Set<String> allowedSortFields) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, defaultSortField);
        }
        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        if (allowedSortFields != null && allowedSortFields.contains(field)) {
            return Sort.by(direction, field);
        }
        return Sort.by(Sort.Direction.DESC, defaultSortField);
    }

    public static <T, R> PageResponse<R> toPageResponse(Page<T> page, Function<T, R> mapper) {
        List<R> content = page.getContent().stream().map(mapper).toList();
        return toPageResponse(page, content);
    }

    public static <R> PageResponse<R> toPageResponse(Page<?> page, List<R> content) {
        return PageResponse.<R>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
