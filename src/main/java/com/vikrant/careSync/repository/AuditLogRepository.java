package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUsername(String username);

    @Query("SELECT a FROM AuditLog a WHERE (:username IS NULL OR a.username = :username) "
            + "AND (:entityId IS NULL OR a.entityId LIKE CONCAT('%', :entityId, '%')) "
            + "AND a.timestamp >= :fromTime AND a.timestamp < :toTime "
            + "ORDER BY a.timestamp DESC")
    Page<AuditLog> search(@Param("username") String username, @Param("entityId") String entityId,
            @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime, Pageable pageable);
}
