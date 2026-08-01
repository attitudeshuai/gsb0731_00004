package com.petfoster.repository;

import com.petfoster.entity.FosterReview;
import com.petfoster.repository.projection.DateCount;
import com.petfoster.repository.projection.MonthlyFostererRating;
import com.petfoster.repository.projection.RatingAggregate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FosterReviewRepository extends JpaRepository<FosterReview, Long> {
    List<FosterReview> findByRevieweeId(Long revieweeId);
    List<FosterReview> findByReviewerId(Long reviewerId);
    boolean existsByRequestIdAndReviewerId(Long requestId, Long reviewerId);

    @Query("SELECT r FROM FosterReview r WHERE " +
           "(:requestId IS NULL OR r.requestId = :requestId) AND " +
           "(:reviewerId IS NULL OR r.reviewerId = :reviewerId) AND " +
           "(:revieweeId IS NULL OR r.revieweeId = :revieweeId) AND " +
           "(:minRating IS NULL OR r.rating >= :minRating) AND " +
           "(:maxRating IS NULL OR r.rating <= :maxRating)")
    Page<FosterReview> searchReviews(
            @Param("requestId") Long requestId,
            @Param("reviewerId") Long reviewerId,
            @Param("revieweeId") Long revieweeId,
            @Param("minRating") Integer minRating,
            @Param("maxRating") Integer maxRating,
            Pageable pageable
    );

    @Query("SELECT AVG(r.rating) FROM FosterReview r WHERE r.revieweeId = :revieweeId")
    Double findAverageRatingByRevieweeId(@Param("revieweeId") Long revieweeId);

    @Query("SELECT AVG(r.responsibilityRating) FROM FosterReview r WHERE r.revieweeId = :revieweeId")
    Double findAverageResponsibilityByRevieweeId(@Param("revieweeId") Long revieweeId);

    @Query("SELECT AVG(r.communicationRating) FROM FosterReview r WHERE r.revieweeId = :revieweeId")
    Double findAverageCommunicationByRevieweeId(@Param("revieweeId") Long revieweeId);

    @Query("SELECT AVG(r.petConditionRating) FROM FosterReview r WHERE r.revieweeId = :revieweeId")
    Double findAveragePetConditionByRevieweeId(@Param("revieweeId") Long revieweeId);

    long countByRevieweeId(Long revieweeId);

    @Query("SELECT CAST(r.createdAt AS LocalDate) AS date, COUNT(r) AS count " +
           "FROM FosterReview r WHERE r.createdAt >= :start AND r.createdAt < :end " +
           "GROUP BY CAST(r.createdAt AS LocalDate)")
    List<DateCount> countDailyByCreatedAtBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r.revieweeId AS revieweeId, " +
           "AVG(r.rating) AS avgRating, " +
           "AVG(r.responsibilityRating) AS avgResponsibility, " +
           "AVG(r.communicationRating) AS avgCommunication, " +
           "AVG(r.petConditionRating) AS avgPetCondition, " +
           "COUNT(r) AS reviewCount " +
           "FROM FosterReview r GROUP BY r.revieweeId")
    List<RatingAggregate> findRatingAggregates();

    @Query("SELECT rv.revieweeId AS fostererId, " +
           "FUNCTION('DATE_FORMAT', rv.createdAt, '%Y-%m') AS month, " +
           "AVG(rv.rating) AS avgRating, " +
           "COUNT(rv) AS reviewCount " +
           "FROM FosterReview rv, FosterRequest r " +
           "WHERE rv.requestId = r.id AND rv.revieweeId = r.fostererId " +
           "AND rv.createdAt >= :start AND rv.createdAt < :end " +
           "GROUP BY rv.revieweeId, FUNCTION('DATE_FORMAT', rv.createdAt, '%Y-%m')")
    List<MonthlyFostererRating> findMonthlyFostererRatings(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
