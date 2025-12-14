package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.service.interfaces.IPatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PatientService implements IPatientService {

    private final PatientRepository patientRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Optional<Patient> getPatientById(Long id) {
        return patientRepository.findById(id);
    }

    public Optional<Patient> getPatientByUsername(String username) {
        return patientRepository.findByUsername(username);
    }

    public Patient updatePatientProfile(Long patientId, Patient updatedPatient) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setFirstName(updatedPatient.getFirstName());
        patient.setLastName(updatedPatient.getLastName());
        patient.setDateOfBirth(updatedPatient.getDateOfBirth());
        patient.setContactInfo(updatedPatient.getContactInfo());
        patient.setIllnessDetails(updatedPatient.getIllnessDetails());
        patient.setGender(updatedPatient.getGender());
        patient.setEmail(updatedPatient.getEmail());
        patient.setIsActive(updatedPatient.getIsActive());

        return patientRepository.save(patient);
    }

    public Patient getPatientProfile(String username) {
        return patientRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
    }

    // Medical History Management
    public MedicalHistory addMedicalHistory(Long patientId, MedicalHistory medicalHistory) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        medicalHistory.setPatient(patient);
        return medicalHistoryRepository.save(medicalHistory);
    }

    public List<MedicalHistory> getPatientMedicalHistory(Long patientId) {
        return medicalHistoryRepository.findByPatientId(patientId);
    }

    public MedicalHistory updateMedicalHistory(Long historyId, MedicalHistory updatedHistory) {
        MedicalHistory history = medicalHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Medical history not found"));

        history.setVisitDate(updatedHistory.getVisitDate());
        history.setSymptoms(updatedHistory.getSymptoms());
        history.setDiagnosis(updatedHistory.getDiagnosis());
        history.setTreatment(updatedHistory.getTreatment());

        return medicalHistoryRepository.save(history);
    }

    public void deleteMedicalHistory(Long historyId) {
        medicalHistoryRepository.deleteById(historyId);
    }

    public List<MedicalHistory> getMedicalHistoryByDateRange(Long patientId, LocalDate startDate, LocalDate endDate) {
        return medicalHistoryRepository.findByPatientId(patientId).stream()
                .filter(history -> !history.getVisitDate().isBefore(startDate)
                        && !history.getVisitDate().isAfter(endDate))
                .toList();
    }

    public List<Patient> getPatientsByIllness(String illnessKeyword) {
        return patientRepository.findAll().stream()
                .filter(patient -> patient.getIllnessDetails() != null &&
                        patient.getIllnessDetails().toLowerCase().contains(illnessKeyword.toLowerCase()))
                .toList();
    }

    public Patient updateIllnessDetails(Long patientId, String illnessDetails) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setIllnessDetails(illnessDetails);
        return patientRepository.save(patient);
    }

    public Patient updateContactInfo(Long patientId, String contactInfo) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setContactInfo(contactInfo);
        return patientRepository.save(patient);
    }

    public Patient updateProfileImage(Long patientId, String imageUrl) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setProfileImageUrl(imageUrl);
        return patientRepository.save(patient);
    }

    public Patient updateProfileImageByUsername(String username, String imageUrl) {
        Patient patient = patientRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setProfileImageUrl(imageUrl);
        patient.setUpdatedAt(LocalDateTime.now());
        return patientRepository.save(patient);
    }
}