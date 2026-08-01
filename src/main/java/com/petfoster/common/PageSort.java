package com.petfoster.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.Map;

public final class PageSort {

    private PageSort() {
    }

    public static Pageable of(int page, int size, String sort,
                              String defaultField, Map<String, String> allowedFieldMappings) {
        return PageRequest.of(page, size, parse(sort, defaultField, allowedFieldMappings));
    }

    public static Sort parse(String sort, String defaultField, Map<String, String> allowedFieldMappings) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        String entityField = allowedFieldMappings.get(field);
        if (entityField == null) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
        return Sort.by(direction, entityField);
    }
}
