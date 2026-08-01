package com.petfoster.repository;

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

    List<FosterRequest> findByStatus(FosterRequest.Status status);

    @Query("SELECT COUNT(r) FROM FosterRequest r WHERE r.createdAt >= :from AND r.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT FUNCTION('DATE', r.createdAt), COUNT(r) FROM FosterRequest r " +
           "WHERE r.createdAt >= :from AND r.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', r.createdAt)")
    List<Object[]> countGroupByDate(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT FUNCTION('DATE', r.createdAt), COUNT(r) FROM FosterRequest r " +
           "WHERE r.status = :status AND r.createdAt >= :from AND r.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', r.createdAt)")
    List<Object[]> countByStatusGroupByDate(@Param("status") FosterRequest.Status status,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(NULLIF(p.breed, ''), '未知品种'), MIN(p.species), COUNT(r) " +
           "FROM FosterRequest r JOIN Pet p ON r.petId = p.id " +
           "GROUP BY COALESCE(NULLIF(p.breed, ''), '未知品种') " +
           "ORDER BY COUNT(r) DESC, COALESCE(NULLIF(p.breed, ''), '未知品种')")
    List<Object[]> countGroupByBreed();

    @Query("SELECT r.fostererId, " +
           "SUM(CASE WHEN r.status = com.petfoster.entity.FosterRequest$Status.Completed THEN 1 ELSE 0 END), " +
           "COUNT(r) FROM FosterRequest r " +
           "WHERE r.fostererId IS NOT NULL AND r.endDate >= :from AND r.endDate <= :to " +
           "GROUP BY r.fostererId " +
           "ORDER BY SUM(CASE WHEN r.status = com.petfoster.entity.FosterRequest$Status.Completed THEN 1 ELSE 0 END) DESC, " +
           "COUNT(r) DESC")
    List<Object[]> countGroupByFosterer(@Param("from") LocalDate from, @Param("to") LocalDate to);

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
}
