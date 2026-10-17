package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.PatientSavedDoctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientSavedDoctorRepository extends JpaRepository<PatientSavedDoctor, Long> {

    /** Newest save first */
    @Query("SELECT s.doctorId FROM PatientSavedDoctor s WHERE s.patientId = :patientId ORDER BY s.createdAt DESC, s.id DESC")
    List<Long> findDoctorIdsByPatientId(@Param("patientId") Long patientId);

    boolean existsByPatientIdAndDoctorId(Long patientId, Long doctorId);

    long countByPatientId(Long patientId);

    @Modifying
    @Query("DELETE FROM PatientSavedDoctor s WHERE s.patientId = :patientId AND s.doctorId = :doctorId")
    int deleteByPatientIdAndDoctorId(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId);
}
