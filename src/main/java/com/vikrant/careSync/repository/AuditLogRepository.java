package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUsername(String username);

    List<AuditLog> findByEntityNameAndEntityId(String entityName, String entityId);
}
