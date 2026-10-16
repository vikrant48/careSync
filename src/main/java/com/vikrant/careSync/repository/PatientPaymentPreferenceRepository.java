package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.PatientPaymentPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientPaymentPreferenceRepository extends JpaRepository<PatientPaymentPreference, Long> {

    Optional<PatientPaymentPreference> findByPatientId(Long patientId);

    @Modifying
    @Query("DELETE FROM PatientPaymentPreference p WHERE p.patientId = :patientId")
    int deleteByPatientId(@Param("patientId") Long patientId);
}
