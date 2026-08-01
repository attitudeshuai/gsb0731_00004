package com.petfoster.repository;

import com.petfoster.common.DailyCountProjection;
import com.petfoster.entity.FosterReview;
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

    @Query(value = "SELECT CAST(r.created_at AS date) AS date, COUNT(r.id) AS count " +
           "FROM foster_reviews r " +
           "WHERE r.created_at >= :start AND r.created_at < :end " +
           "GROUP BY CAST(r.created_at AS date) " +
           "ORDER BY date", nativeQuery = true)
    List<DailyCountProjection> countDailyByCreatedAtBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r.revieweeId AS userId, " +
           "AVG(r.rating) AS avgRating, " +
           "AVG(r.responsibilityRating) AS avgResponsibility, " +
           "AVG(r.communicationRating) AS avgCommunication, " +
           "AVG(r.petConditionRating) AS avgPetCondition, " +
           "COUNT(r) AS reviewCount " +
           "FROM FosterReview r " +
           "GROUP BY r.revieweeId")
    List<ReviewAggregation> aggregateAllByReviewee();

    interface ReviewAggregation {
        Long getUserId();
        Double getAvgRating();
        Double getAvgResponsibility();
        Double getAvgCommunication();
        Double getAvgPetCondition();
        Long getReviewCount();
    }

    @Query(value = "SELECT r.reviewee_id AS userId, " +
           "DATE_FORMAT(r.created_at, '%Y-%m') AS month, " +
           "AVG(r.rating) AS avgRating, " +
           "COUNT(r.id) AS reviewCount " +
           "FROM foster_reviews r " +
           "WHERE r.created_at >= :start AND r.created_at < :end " +
           "GROUP BY r.reviewee_id, DATE_FORMAT(r.created_at, '%Y-%m')",
           nativeQuery = true)
    List<MonthlyReviewAggregation> aggregateMonthlyByReviewee(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    interface MonthlyReviewAggregation {
        Long getUserId();
        String getMonth();
        Double getAvgRating();
        Long getReviewCount();
    }
}
