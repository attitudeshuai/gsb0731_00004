package com.petfoster.repository.projection;

public interface MonthlyFostererCount {
    Long getFostererId();
    String getMonth();
    Long getCompletedCount();
    Long getTotalCount();
}
