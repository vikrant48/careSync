package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.MedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {
    List<MedicalHistory> findByPatientId(Long patientId);
    
    @Query("SELECT mh FROM MedicalHistory mh WHERE mh.patient.id = :patientId ORDER BY mh.visitDate DESC")
    List<MedicalHistory> findByPatientIdOrderByVisitDateDesc(@Param("patientId") Long patientId);
} 