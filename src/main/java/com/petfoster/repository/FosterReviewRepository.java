package com.petfoster.repository;

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

    @Query("SELECT FUNCTION('DATE', r.createdAt), COUNT(r) FROM FosterReview r " +
           "WHERE r.createdAt >= :from AND r.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', r.createdAt)")
    List<Object[]> countGroupByDate(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT r.revieweeId, AVG(r.rating), AVG(r.responsibilityRating), " +
           "AVG(r.communicationRating), AVG(r.petConditionRating), COUNT(r) " +
           "FROM FosterReview r GROUP BY r.revieweeId " +
           "ORDER BY AVG(r.rating) DESC, COUNT(r) DESC")
    List<Object[]> findTopRatedUsers(Pageable pageable);

    @Query("SELECT r.revieweeId, AVG(r.rating) FROM FosterReview r " +
           "WHERE r.createdAt >= :from AND r.createdAt < :to " +
           "GROUP BY r.revieweeId")
    List<Object[]> averageRatingGroupByReviewee(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);
}
