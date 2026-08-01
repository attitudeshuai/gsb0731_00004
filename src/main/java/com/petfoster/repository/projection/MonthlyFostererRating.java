package com.petfoster.repository.projection;

public interface MonthlyFostererRating {
    Long getFostererId();
    String getMonth();
    Double getAvgRating();
    Long getReviewCount();
}
