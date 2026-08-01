package com.petfoster.common;

import com.petfoster.dto.StatsDTO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DateBucketUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateBucketUtils() {
    }

    public static LocalDate[] normalizeRange(LocalDate start, LocalDate end) {
        LocalDate s = start != null ? start : LocalDate.now().minusDays(30);
        LocalDate e = end != null ? end : LocalDate.now();
        if (s.isAfter(e)) {
            LocalDate tmp = s;
            s = e;
            e = tmp;
        }
        return new LocalDate[]{s, e};
    }

    public static List<LocalDate> dateRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    public static Map<LocalDate, Long> toMap(List<? extends DailyCountProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return new HashMap<>();
        }
        return projections.stream()
                .collect(Collectors.toMap(
                        DailyCountProjection::getDate,
                        DailyCountProjection::getCount,
                        Long::sum
                ));
    }

    public static List<StatsDTO.DailyCount> fillDailyCounts(
            LocalDate start, LocalDate end,
            List<? extends DailyCountProjection> projections) {

        Map<LocalDate, Long> countMap = toMap(projections);
        return dateRange(start, end).stream()
                .map(d -> StatsDTO.DailyCount.builder()
                        .date(d.format(DATE_FORMATTER))
                        .count(countMap.getOrDefault(d, 0L))
                        .build())
                .toList();
    }
}
