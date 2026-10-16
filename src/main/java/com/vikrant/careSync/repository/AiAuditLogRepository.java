package com.vikrant.careSync.repository;

import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.vikrant.careSync.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {

    // Keeps usage statistics, drops the text (prompts and answers contain patient data)
    @Modifying
    @Query("UPDATE AiAuditLog a SET a.userPrompt = NULL, a.aiResponse = NULL, a.errorMessage = NULL "
            + "WHERE a.createdAt < :cutoff AND (a.userPrompt IS NOT NULL OR a.aiResponse IS NOT NULL "
            + "OR a.errorMessage IS NOT NULL)")
    int clearTextOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
