package com.petfoster.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class DateBuckets {

    private DateBuckets() {
    }

    public static <T> List<T> fill(LocalDate startInclusive, LocalDate endInclusive,
                                   Map<LocalDate, Long> countsByDate,
                                   BiFunction<LocalDate, Long, T> factory) {
        List<T> result = new ArrayList<>();
        for (LocalDate date = startInclusive; !date.isAfter(endInclusive); date = date.plusDays(1)) {
            result.add(factory.apply(date, countsByDate.getOrDefault(date, 0L)));
        }
        return result;
    }
}
