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

    List<MedicalHistory> findAllByAppointmentId(Long appointmentId);

    @Query("SELECT mh FROM MedicalHistory mh WHERE mh.patient.id = :patientId ORDER BY mh.visitDate DESC")
    List<MedicalHistory> findByPatientIdOrderByVisitDateDesc(@Param("patientId") Long patientId);

    // Filtering and limiting in SQL: each loaded row decrypts up to 11 fields
    @Query("SELECT mh FROM MedicalHistory mh WHERE mh.patient.id = :patientId "
            + "AND (mh.isDraft IS NULL OR mh.isDraft = false)")
    List<MedicalHistory> findFinalisedByPatientId(@Param("patientId") Long patientId);

    @Query("SELECT mh FROM MedicalHistory mh WHERE mh.patient.id = :patientId ORDER BY mh.visitDate DESC")
    List<MedicalHistory> findRecentByPatientId(@Param("patientId") Long patientId,
            org.springframework.data.domain.Pageable pageable);

    @Override
    Optional<MedicalHistory> findById(Long id);

    // Access-control lookup (AccessGuard): owning patient id only
    @Query("SELECT p.id FROM MedicalHistory mh JOIN mh.patient p WHERE mh.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);

    @Override
    <S extends MedicalHistory> S save(S entity);

    @Override
    void deleteById(Long id);

    @Query("SELECT mh FROM MedicalHistory mh WHERE mh.patient.id = :patientId AND mh.visitDate BETWEEN :startDate AND :endDate ORDER BY mh.visitDate DESC")
    List<MedicalHistory> findByPatientIdAndVisitDateBetween(@Param("patientId") Long patientId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}