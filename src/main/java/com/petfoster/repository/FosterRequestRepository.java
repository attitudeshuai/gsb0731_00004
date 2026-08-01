package com.petfoster.repository;

import com.petfoster.common.DailyCountProjection;
import com.petfoster.entity.FosterRequest;
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
public interface FosterRequestRepository extends JpaRepository<FosterRequest, Long> {
    List<FosterRequest> findByOwnerId(Long ownerId);
    List<FosterRequest> findByFostererId(Long fostererId);

    @Query("SELECT r FROM FosterRequest r WHERE r.ownerId = :userId OR r.fostererId = :userId")
    List<FosterRequest> findByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM FosterRequest r JOIN Pet p ON r.petId = p.id WHERE " +
           "(r.ownerId = :userId OR r.fostererId = :userId) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:startDateFrom IS NULL OR r.startDate >= :startDateFrom) AND " +
           "(:startDateTo IS NULL OR r.startDate <= :startDateTo) AND " +
           "(:breed IS NULL OR p.breed = :breed)")
    Page<FosterRequest> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("status") FosterRequest.Status status,
            @Param("startDateFrom") LocalDate startDateFrom,
            @Param("startDateTo") LocalDate startDateTo,
            @Param("breed") String breed,
            Pageable pageable
    );

    @Query("SELECT r FROM FosterRequest r JOIN Pet p ON r.petId = p.id WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:ownerId IS NULL OR r.ownerId = :ownerId) AND " +
           "(:fostererId IS NULL OR r.fostererId = :fostererId) AND " +
           "(:petId IS NULL OR r.petId = :petId) AND " +
           "(:startDateFrom IS NULL OR r.startDate >= :startDateFrom) AND " +
           "(:startDateTo IS NULL OR r.startDate <= :startDateTo) AND " +
           "(:breed IS NULL OR p.breed = :breed)")
    Page<FosterRequest> searchRequests(
            @Param("status") FosterRequest.Status status,
            @Param("ownerId") Long ownerId,
            @Param("fostererId") Long fostererId,
            @Param("petId") Long petId,
            @Param("startDateFrom") LocalDate startDateFrom,
            @Param("startDateTo") LocalDate startDateTo,
            @Param("breed") String breed,
            Pageable pageable
    );

    long countByStatus(FosterRequest.Status status);

    @Query("SELECT COUNT(r) FROM FosterRequest r WHERE r.ownerId = :userId OR r.fostererId = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM FosterRequest r WHERE r.petId = :petId " +
           "AND r.status IN (com.petfoster.entity.FosterRequest$Status.Pending, " +
           "com.petfoster.entity.FosterRequest$Status.Approved, " +
           "com.petfoster.entity.FosterRequest$Status.InProgress) " +
           "AND r.startDate <= :endDate AND r.endDate >= :startDate " +
           "AND (:excludeId IS NULL OR r.id != :excludeId)")
    List<FosterRequest> findConflictingRequests(
            @Param("petId") Long petId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);

    @Query("SELECT r FROM FosterRequest r WHERE r.status = com.petfoster.entity.FosterRequest$Status.Approved " +
           "AND r.startDate < :threshold")
    List<FosterRequest> findApprovedBeforeDate(@Param("threshold") LocalDate threshold);

    @Query("SELECT r FROM FosterRequest r WHERE r.status = com.petfoster.entity.FosterRequest$Status.InProgress " +
           "AND r.startDate <= :date AND r.endDate >= :date")
    List<FosterRequest> findInProgressOnDate(@Param("date") LocalDate date);

    @Query("SELECT r FROM FosterRequest r WHERE r.status = com.petfoster.entity.FosterRequest$Status.InProgress " +
           "AND r.endDate = :endDate")
    List<FosterRequest> findInProgressEndingOnDate(@Param("endDate") LocalDate endDate);

    long countByOwnerId(Long ownerId);

    @Query("SELECT COUNT(r) FROM FosterRequest r WHERE r.fostererId = :userId " +
           "AND r.status = com.petfoster.entity.FosterRequest$Status.Completed")
    long countCompletedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(r) FROM FosterRequest r WHERE " +
           "(r.ownerId = :userId OR r.fostererId = :userId) " +
           "AND r.status = com.petfoster.entity.FosterRequest$Status.Cancelled")
    long countCancelledByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT CAST(r.created_at AS date) AS date, COUNT(r.id) AS count " +
           "FROM foster_requests r " +
           "WHERE r.created_at >= :start AND r.created_at < :end " +
           "GROUP BY CAST(r.created_at AS date) " +
           "ORDER BY date", nativeQuery = true)
    List<DailyCountProjection> countDailyByCreatedAtBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT CAST(r.created_at AS date) AS date, COUNT(r.id) AS count " +
           "FROM foster_requests r " +
           "WHERE r.created_at >= :start AND r.created_at < :end " +
           "AND r.status = 'Completed' " +
           "GROUP BY CAST(r.created_at AS date) " +
           "ORDER BY date", nativeQuery = true)
    List<DailyCountProjection> countDailyCompletedByCreatedAtBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT r FROM FosterRequest r WHERE r.status = com.petfoster.entity.FosterRequest$Status.Completed")
    List<FosterRequest> findAllCompleted();

    @Query(value = "SELECT r.fosterer_id AS fostererId, " +
           "DATE_FORMAT(r.end_date, '%Y-%m') AS month, " +
           "SUM(CASE WHEN r.status = 'Completed' THEN 1 ELSE 0 END) AS completedCount, " +
           "SUM(CASE WHEN r.status = 'Cancelled' THEN 1 ELSE 0 END) AS cancelledCount " +
           "FROM foster_requests r " +
           "WHERE r.fosterer_id IS NOT NULL " +
           "AND r.end_date >= :start AND r.end_date < :end " +
           "AND r.status IN ('Completed', 'Cancelled') " +
           "GROUP BY r.fosterer_id, DATE_FORMAT(r.end_date, '%Y-%m')",
           nativeQuery = true)
    List<FostererMonthlyCount> aggregateMonthlyByFosterer(
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    interface FostererMonthlyCount {
        Long getFostererId();
        String getMonth();
        Long getCompletedCount();
        Long getCancelledCount();
    }
}
