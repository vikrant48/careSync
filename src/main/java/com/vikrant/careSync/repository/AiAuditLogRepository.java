package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {
    List<AiAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AiAuditLog> findTop50ByOrderByCreatedAtDesc();
}
