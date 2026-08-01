package com.petfoster.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 分页与排序解析工具：各模块只需声明自己的排序字段白名单（含别名映射）和默认排序字段。
 * 排序参数格式为 "field,direction"，如 "createdAt,desc"；未识别字段一律回退到默认字段降序。
 */
public final class PageUtils {

    private PageUtils() {
    }

    public static Sort parseSort(String sort, Map<String, String> allowedFields, String defaultField) {
        if (StringUtils.hasText(sort)) {
            String[] parts = sort.split(",");
            String field = allowedFields.get(parts[0]);
            if (field != null) {
                Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                        ? Sort.Direction.ASC : Sort.Direction.DESC;
                return Sort.by(direction, field);
            }
        }
        return Sort.by(Sort.Direction.DESC, defaultField);
    }

    public static Pageable pageable(int page, int size, String sort,
                                    Map<String, String> allowedFields, String defaultField) {
        return PageRequest.of(page, size, parseSort(sort, allowedFields, defaultField));
    }
}
