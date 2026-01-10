package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.MedicalHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {

    List<MedicalHistory> findByPatientId(Long patientId);

    Optional<MedicalHistory> findByAppointmentId(Long appointmentId);

    @Query("SELECT mh FROM MedicalHistory mh WHERE mh.patient.id = :patientId ORDER BY mh.visitDate DESC")
    List<MedicalHistory> findByPatientIdOrderByVisitDateDesc(@Param("patientId") Long patientId);

    @Override
    Optional<MedicalHistory> findById(Long id);

    @Override
    <S extends MedicalHistory> S save(S entity);

    @Override
    void deleteById(Long id);

    @Query("SELECT mh FROM MedicalHistory mh WHERE mh.patient.id = :patientId AND mh.visitDate BETWEEN :startDate AND :endDate ORDER BY mh.visitDate DESC")
    List<MedicalHistory> findByPatientIdAndVisitDateBetween(@Param("patientId") Long patientId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}