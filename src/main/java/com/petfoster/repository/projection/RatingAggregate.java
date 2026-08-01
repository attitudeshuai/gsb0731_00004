package com.petfoster.repository.projection;

public interface RatingAggregate {
    Long getRevieweeId();
    Double getAvgRating();
    Double getAvgResponsibility();
    Double getAvgCommunication();
    Double getAvgPetCondition();
    Long getReviewCount();
}
