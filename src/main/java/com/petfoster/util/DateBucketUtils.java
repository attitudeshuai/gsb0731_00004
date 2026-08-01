package com.petfoster.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 按日期分桶统计的公共能力：配合 Repository 的 GROUP BY 日期聚合查询使用。
 * 负责生成连续日期区间、把聚合结果转成 日期→数量 映射、并将缺失日期补零后映射为输出元素，
 * 业务方不再需要在内存里遍历整表做分桶。
 */
public final class DateBucketUtils {

    private DateBucketUtils() {
    }

    /**
     * 生成 [start, end] 闭区间内的连续日期列表。
     */
    public static List<LocalDate> range(LocalDate start, LocalDate end) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }

    /**
     * 把 Repository 按日期分组查询的行（[日期, 数量]）转成 Map，
     * 兼容数据库函数返回的 java.sql.Date / LocalDateTime / LocalDate / String。
     */
    public static Map<LocalDate, Long> toCountMap(List<Object[]> rows) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = toLocalDate(row[0]);
            if (day != null) {
                counts.merge(day, ((Number) row[1]).longValue(), Long::sum);
            }
        }
        return counts;
    }

    /**
     * 遍历完整日期区间（缺失日期补零），逐日映射为输出元素。
     */
    public static <T> List<T> fillGaps(List<LocalDate> days, Map<LocalDate, Long> counts,
                                       BiFunction<LocalDate, Long, T> mapper) {
        return days.stream()
                .map(day -> mapper.apply(day, counts.getOrDefault(day, 0L)))
                .toList();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        if (value instanceof String str) {
            return LocalDate.parse(str);
        }
        return null;
    }
}
