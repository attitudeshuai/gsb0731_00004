package com.petfoster.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 按日期分桶的公共能力。
 *
 * <p>此前统计看板里「按日期统计」的几个指标各自复制了一遍相同的循环：先建连续日期区间，
 * 再把数据库记录逐条塞进 {@code Map<LocalDate, Long>}，最后对区间内每一天取值（缺省 0）
 * 生成一条结果。此类把这套「连续区间 + 零填充分桶」逻辑收敛为可复用的静态能力，
 * 并配合仓储层的 {@code GROUP BY} 聚合结果使用，避免再整表拉数据在内存里慢慢算。
 */
public final class DateBuckets {

    private DateBuckets() {
    }

    /** 生成 [start, end] 闭区间内的连续日期列表。 */
    public static List<LocalDate> range(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /**
     * 把数据库按日期聚合的原始结果（每行 {@code [日期, 数量]}）转换为 {@code LocalDate -> count} 映射。
     * 日期列兼容 {@link LocalDate}、{@link java.sql.Date}、{@link LocalDateTime} 与 {@link java.util.Date}。
     */
    public static Map<LocalDate, Long> toCountMap(List<Object[]> rows) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            if (date != null) {
                counts.merge(date, ((Number) row[1]).longValue(), Long::sum);
            }
        }
        return counts;
    }

    /**
     * 按连续日期区间做零填充分桶：对 [start, end] 内的每一天，用 counts 中的值（缺省 0）
     * 交给 mapper 生成结果元素。
     */
    public static <T> List<T> series(LocalDate start, LocalDate end,
                                     Map<LocalDate, Long> counts,
                                     BiFunction<LocalDate, Long, T> mapper) {
        return range(start, end).stream()
                .map(d -> mapper.apply(d, counts.getOrDefault(d, 0L)))
                .toList();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        throw new IllegalArgumentException("无法识别的日期类型: " + value.getClass());
    }
}
