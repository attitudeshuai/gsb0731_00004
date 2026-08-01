package com.petfoster.repository;

import com.petfoster.entity.FosterReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    /** 按创建日期分组的新增评价数：每行 [date, count]。 */
    @Query("SELECT CAST(r.createdAt AS date), COUNT(r) FROM FosterReview r " +
           "WHERE r.createdAt >= :start AND r.createdAt < :end " +
           "GROUP BY CAST(r.createdAt AS date)")
    List<Object[]> countDailyCreatedBetween(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /** 收到过评价的用户及其各维度平均分（按评分、评价数排序）：
     *  每行 [userId, username, avgRating, avgResponsibility, avgCommunication, avgPetCondition, count]。 */
    @Query("SELECT u.id, u.username, AVG(r.rating), AVG(r.responsibilityRating), " +
           "AVG(r.communicationRating), AVG(r.petConditionRating), COUNT(r) " +
           "FROM FosterReview r JOIN User u ON r.revieweeId = u.id " +
           "GROUP BY u.id, u.username " +
           "ORDER BY AVG(r.rating) DESC, COUNT(r) DESC")
    List<Object[]> aggregateTopRatedUsers();

    /** 按月份+寄养人聚合收到的评价平均分（以关联寄养申请的结束日期归月，且被评价人为寄养人）：
     *  每行 [year, month, fostererId, avgRating]。 */
    @Query("SELECT YEAR(req.endDate), MONTH(req.endDate), r.revieweeId, AVG(r.rating) " +
           "FROM FosterReview r JOIN FosterRequest req ON r.requestId = req.id " +
           "WHERE r.revieweeId = req.fostererId AND req.endDate >= :start AND req.endDate <= :end " +
           "GROUP BY YEAR(req.endDate), MONTH(req.endDate), r.revieweeId")
    List<Object[]> aggregateFostererMonthlyRatings(@Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);
}
