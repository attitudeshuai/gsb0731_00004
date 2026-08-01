package com.petfoster.common;

import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一的排序解析器。
 *
 * <p>以前每个 Service 都各自复制一份 {@code parseSort(String)}：解析 "字段,方向" 字符串、
 * 缺省降序、并用一个白名单 switch 决定允许排序的字段。此类把这套逻辑收敛为可配置的白名单，
 * 各 Service 只需声明自己允许的字段与默认字段即可，行为与原来的实现保持完全一致：
 * <ul>
 *     <li>sort 为空 → 按默认字段降序</li>
 *     <li>方向仅当第二段为 "asc"(忽略大小写)时升序，否则降序</li>
 *     <li>字段不在白名单内 → 回退到默认字段并强制降序</li>
 * </ul>
 */
public final class SortResolver {

    private final Map<String, String> allowedFields;
    private final String defaultField;

    private SortResolver(String defaultField, Map<String, String> allowedFields) {
        this.defaultField = defaultField;
        this.allowedFields = allowedFields;
    }

    public static Builder withDefault(String defaultField) {
        return new Builder(defaultField);
    }

    public Sort resolve(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        String property = allowedFields.get(field);
        if (property == null) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
        return Sort.by(direction, property);
    }

    public static final class Builder {
        private final String defaultField;
        private final Map<String, String> allowed = new LinkedHashMap<>();

        private Builder(String defaultField) {
            this.defaultField = defaultField;
        }

        /** 允许一个既是请求参数名、也是实体属性名的排序字段。 */
        public Builder allow(String field) {
            allowed.put(field, field);
            return this;
        }

        /** 允许一个请求参数名(别名)，映射到指定的实体属性名。 */
        public Builder alias(String field, String entityProperty) {
            allowed.put(field, entityProperty);
            return this;
        }

        public SortResolver build() {
            return new SortResolver(defaultField, Map.copyOf(allowed));
        }
    }
}
