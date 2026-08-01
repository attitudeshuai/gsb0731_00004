package com.petfoster.util;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class EntityCollections {

    private EntityCollections() {
    }

    public static <T> Map<Long, T> toIdMap(Collection<T> entities, Function<T, Long> idExtractor) {
        return entities.stream()
                .collect(Collectors.toMap(idExtractor, Function.identity(), (a, b) -> a));
    }
}
