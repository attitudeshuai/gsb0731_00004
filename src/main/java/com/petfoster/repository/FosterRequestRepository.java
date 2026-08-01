package com.petfoster.repository;

import com.petfoster.entity.FosterRequest;
import com.petfoster.repository.projection.BreedCount;
import com.petfoster.repository.projection.DateCount;
import com.petfoster.repository.projection.MonthlyFostererCount;
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

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT CAST(r.createdAt AS LocalDate) AS date, COUNT(r) AS count " +
           "FROM FosterRequest r WHERE r.createdAt >= :start AND r.createdAt < :end " +
           "GROUP BY CAST(r.createdAt AS LocalDate)")
    List<DateCount> countDailyByCreatedAtBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT CAST(r.createdAt AS LocalDate) AS date, COUNT(r) AS count " +
           "FROM FosterRequest r WHERE r.status = com.petfoster.entity.FosterRequest$Status.Completed " +
           "AND r.createdAt >= :start AND r.createdAt < :end " +
           "GROUP BY CAST(r.createdAt AS LocalDate)")
    List<DateCount> countCompletedDailyByCreatedAtBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT p.breed AS breed, p.species AS species, COUNT(r) AS count " +
           "FROM FosterRequest r JOIN Pet p ON r.petId = p.id " +
           "GROUP BY p.breed, p.species")
    List<BreedCount> countByBreed();

    @Query("SELECT r FROM FosterRequest r JOIN FETCH r.pet " +
           "WHERE r.status = com.petfoster.entity.FosterRequest$Status.Completed")
    List<FosterRequest> findCompletedWithPet();

    @Query("SELECT r.fostererId AS fostererId, " +
           "FUNCTION('DATE_FORMAT', r.createdAt, '%Y-%m') AS month, " +
           "SUM(CASE WHEN r.status = com.petfoster.entity.FosterRequest$Status.Completed THEN 1 ELSE 0 END) AS completedCount, " +
           "COUNT(r) AS totalCount " +
           "FROM FosterRequest r " +
           "WHERE r.fostererId IS NOT NULL " +
           "AND r.status IN (com.petfoster.entity.FosterRequest$Status.Completed, com.petfoster.entity.FosterRequest$Status.Cancelled) " +
           "AND r.createdAt >= :start AND r.createdAt < :end " +
           "GROUP BY r.fostererId, FUNCTION('DATE_FORMAT', r.createdAt, '%Y-%m')")
    List<MonthlyFostererCount> countMonthlyByFosterer(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
