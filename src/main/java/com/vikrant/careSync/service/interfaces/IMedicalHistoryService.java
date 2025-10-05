package com.vikrant.careSync.service.interfaces;

import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.dto.MedicalHistoryWithDoctorDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service interface for Medical History operations
 * Defines all business logic operations related to medical history
 */
public interface IMedicalHistoryService {

    /**
     * Create new medical history
     * @param medicalHistory Medical history to create
     * @return Created medical history
     */
    MedicalHistory createMedicalHistory(MedicalHistory medicalHistory);

    /**
     * Update medical history
     * @param id Medical history ID
     * @param updatedHistory Updated medical history
     * @return Updated medical history
     */
    MedicalHistory updateMedicalHistory(Long id, MedicalHistory updatedHistory);

    /**
     * Get medical history by ID
     * @param id Medical history ID
     * @return Medical history
     */
    MedicalHistory getMedicalHistoryById(Long id);

    /**
     * Get medical history by patient
     * @param patientId Patient ID
     * @return List of medical histories for the patient
     */
    List<MedicalHistory> getMedicalHistoryByPatient(Long patientId);

    /**
     * Get medical history by date range
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate End date
     * @return List of medical histories within date range
     */
    List<MedicalHistory> getMedicalHistoryByDateRange(Long patientId, LocalDate startDate, LocalDate endDate);

    /**
     * Delete medical history
     * @param id Medical history ID
     */
    void deleteMedicalHistory(Long id);

    /**
     * Get medical history summary for a patient
     * @param patientId Patient ID
     * @return Summary map containing statistics
     */
    Map<String, Object> getMedicalHistorySummary(Long patientId);

    /**
     * Get medical history by diagnosis
     * @param patientId Patient ID
     * @param diagnosis Diagnosis to search for
     * @return List of medical histories with matching diagnosis
     */
    List<MedicalHistory> getMedicalHistoryByDiagnosis(Long patientId, String diagnosis);

    /**
     * Create medical history with doctor
     * @param medicalHistory Medical history to create
     * @param doctorId Doctor ID
     * @return Created medical history
     */
    MedicalHistory createMedicalHistoryWithDoctor(MedicalHistory medicalHistory, Long doctorId);

    /**
     * Get recent medical history
     * @param patientId Patient ID
     * @param limit Number of records to return
     * @return List of recent medical histories
     */
    List<MedicalHistory> getRecentMedicalHistory(Long patientId, int limit);

    /**
     * Get medical history by date range (String parameters)
     * @param patientId Patient ID
     * @param startDate Start date as string
     * @param endDate End date as string
     * @return List of medical histories within date range
     */
    List<MedicalHistory> getMedicalHistoryByDateRange(Long patientId, String startDate, String endDate);

    /**
     * Get medical history with doctor information
     * @param patientId Patient ID
     * @return List of medical histories with associated doctor information
     */
    List<MedicalHistoryWithDoctorDto> getMedicalHistoryWithDoctorByPatient(Long patientId);
}