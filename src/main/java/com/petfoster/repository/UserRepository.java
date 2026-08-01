package com.petfoster.repository;

import com.petfoster.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /** 按创建日期分组的新增用户数：每行 [date, count]。 */
    @Query("SELECT CAST(u.createdAt AS date), COUNT(u) FROM User u " +
           "WHERE u.createdAt >= :start AND u.createdAt < :end " +
           "GROUP BY CAST(u.createdAt AS date)")
    List<Object[]> countDailyCreatedBetween(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}
