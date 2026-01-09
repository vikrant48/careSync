package com.vikrant.careSync.service.interfaces;

import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.dto.MedicalHistoryDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for Patient operations
 * Defines all business logic operations related to patients
 */
public interface IPatientService {

    /**
     * Retrieve all patients
     * 
     * @return List of all patients
     */
    List<Patient> getAllPatients();

    /**
     * Get patient by ID
     * 
     * @param id Patient ID
     * @return Optional containing patient if found
     */
    Optional<Patient> getPatientById(Long id);

    Optional<Patient> getPatientByUsername(String username);

    Optional<PatientDto> getPatientDtoById(Long id);

    Optional<PatientDto> getPatientDtoByUsername(String username);

    /**
     * Update patient profile
     * 
     * @param patientId      Patient ID
     * @param updatedPatient Updated patient information
     * @return Updated patient
     */
    Patient updatePatientProfile(Long patientId, Patient updatedPatient);

    /**
     * Get patient profile
     * 
     * @param username Patient username
     * @return Patient profile
     */
    Patient getPatientProfile(String username);

    /**
     * Add medical history to patient
     * 
     * @param patientId      Patient ID
     * @param medicalHistory Medical history to add
     * @return Added medical history
     */
    MedicalHistory addMedicalHistory(Long patientId, MedicalHistory medicalHistory);

    List<MedicalHistory> getPatientMedicalHistory(Long patientId);

    List<MedicalHistoryDto> getPatientMedicalHistoryDto(Long patientId);

    /**
     * Update medical history
     * 
     * @param historyId      Medical history ID
     * @param updatedHistory Updated medical history
     * @return Updated medical history
     */
    MedicalHistory updateMedicalHistory(Long historyId, MedicalHistory updatedHistory);

    /**
     * Delete medical history
     * 
     * @param historyId Medical history ID
     */
    void deleteMedicalHistory(Long historyId);

    /**
     * Get medical history by date range
     * 
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate   End date
     * @return List of medical histories within date range
     */
    List<MedicalHistory> getMedicalHistoryByDateRange(Long patientId, LocalDate startDate, LocalDate endDate);

    /**
     * Get patients by illness keyword
     * 
     * @param illnessKeyword Illness keyword to search
     * @return List of patients with matching illness
     */
    List<Patient> getPatientsByIllness(String illnessKeyword);

    /**
     * Update patient illness details
     * 
     * @param patientId      Patient ID
     * @param illnessDetails New illness details
     * @return Updated patient
     */
    Patient updateIllnessDetails(Long patientId, String illnessDetails);

    /**
     * Update patient contact information
     * 
     * @param patientId   Patient ID
     * @param contactInfo New contact information
     * @return Updated patient
     */
    Patient updateContactInfo(Long patientId, String contactInfo);

    /**
     * Update patient profile image
     * 
     * @param patientId Patient ID
     * @param imageUrl  New image URL
     * @return Updated patient
     */
    Patient updateProfileImage(Long patientId, String imageUrl);

    /**
     * Update patient profile image by username
     * 
     * @param username Patient username
     * @param imageUrl New image URL
     * @return Updated patient
     */
    Patient updateProfileImageByUsername(String username, String imageUrl);
}