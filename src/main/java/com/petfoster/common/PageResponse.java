package com.petfoster.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    /**
     * 用已经转换好的内容列表 + 原始分页信息构建响应。
     * 取代各 Service 中重复的 {@code PageResponse.builder().content(..).pageNumber(page.getNumber())...} 样板。
     */
    public static <T> PageResponse<T> of(List<T> content, Page<?> page) {
        return PageResponse.<T>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    /**
     * 用一个 mapper 把实体分页页转换为 DTO 分页响应。
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return of(page.getContent().stream().map(mapper).toList(), page);
    }
}
