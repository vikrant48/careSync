package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Vital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VitalRepository extends JpaRepository<Vital, Long> {
    List<Vital> findByPatientIdOrderByRecordedAtDesc(Long patientId);
}
