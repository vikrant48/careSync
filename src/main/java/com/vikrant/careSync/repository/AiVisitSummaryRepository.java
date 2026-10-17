package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AiVisitSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiVisitSummaryRepository extends JpaRepository<AiVisitSummary, Long> {
}
