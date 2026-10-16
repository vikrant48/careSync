package com.vikrant.careSync.service;

import com.vikrant.careSync.util.ProfileCompletion;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.MedicalHistory;
import com.vikrant.careSync.repository.PatientRepository;
import com.vikrant.careSync.repository.MedicalHistoryRepository;
import com.vikrant.careSync.service.interfaces.IPatientService;
import com.vikrant.careSync.dto.PatientDto;
import com.vikrant.careSync.dto.MedicalHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService implements IPatientService {

    private final PatientRepository patientRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final CacheManager cacheManager;
    private final PageCache pageCache;
    private final AccountEmailService accountEmailService;
    private final MedicalHistoryService medicalHistoryService;

    @Override
    public List<Patient> getAllPatients() {
        return patientRepository.findAllWithUser();
    }

    @Override
    public long getPatientCount() {
        return pageCache.count("PATIENT:PAGINATED_LIST", "total_patient_count", patientRepository::count);
    }

    @Override
    public List<PatientDto> getPatientsPaginated(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return pageCache.list("PATIENT:PAGINATED_LIST", "page_" + safePage + "_size_" + safeSize,
                () -> fetchPageFromDb(safePage, safeSize));
    }

    private List<PatientDto> fetchPageFromDb(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return patientRepository.findAll(pageable).stream()
                .map(this::convertToDtoWithStats)
                .toList();
    }

    @Cacheable(value = "PATIENT:PROFILE", key = "'id_' + #id")
    public Optional<PatientDto> getPatientDtoById(Long id) {
        return patientRepository.findById(id).map(this::convertToDtoWithStats);
    }

    @Cacheable(value = "PATIENT:PROFILE", key = "'username_' + #username")
    public Optional<PatientDto> getPatientDtoByUsername(String username) {
        return patientRepository.findByUsername(username).map(this::convertToDtoWithStats);
    }

    private PatientDto convertToDtoWithStats(Patient patient) {
        PatientDto dto = new PatientDto(patient);
        dto.setCompletionPercentage(calculateCompletionPercentage(patient));
        return dto;
    }

    private int calculateCompletionPercentage(Patient patient) {
        return new ProfileCompletion()
                // Basic Info (30%)
                .section(30, ProfileCompletion.filled(patient.getFirstName(), patient.getLastName(),
                        patient.getEmail(), patient.getContactInfo()))
                // Personal Details (30%)
                .section(30, patient.getDateOfBirth() != null
                        && ProfileCompletion.filled(patient.getGender(), patient.getBloodGroup()))
                // Profile Image (20%)
                .section(20, ProfileCompletion.filled(patient.getProfileImageUrl()))
                // Medical Context (20%)
                .section(20, ProfileCompletion.filled(patient.getIllnessDetails()))
                .total();
    }

    public Optional<Patient> getPatientById(Long id) {
        return patientRepository.findById(id);
    }

    public Optional<Patient> getPatientByUsername(String username) {
        return patientRepository.findByUsername(username);
    }

    @Caching(evict = {
            @CacheEvict(value = "PATIENT:PROFILE", key = "'id_' + #patientId"),
            @CacheEvict(value = "PATIENT:PROFILE", allEntries = true)
    })
    public Patient updatePatientProfile(Long patientId, Patient updatedPatient) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + patientId));

        // Only the fields that were sent are changed
        setIfPresent(updatedPatient.getFirstName(), patient::setFirstName);
        setIfPresent(updatedPatient.getLastName(), patient::setLastName);
        setIfPresent(updatedPatient.getDateOfBirth(), patient::setDateOfBirth);
        setIfPresent(updatedPatient.getContactInfo(), patient::setContactInfo);
        setIfPresent(updatedPatient.getIllnessDetails(), patient::setIllnessDetails);
        setIfPresent(updatedPatient.getGender(), patient::setGender);
        setIfPresent(updatedPatient.getBloodGroup(), patient::setBloodGroup);
        // isActive is not taken from a profile edit: only admins activate or block accounts
        // (AccountStatusService), otherwise a blocked or treated patient's status could be changed here

        // Email update handling:
        // Safely extract email from attached user object or patient delegate without
        // overwriting with null
        String requestedEmail = (updatedPatient.getUser() != null && updatedPatient.getUser().getEmail() != null)
                ? updatedPatient.getUser().getEmail()
                : updatedPatient.getEmail();

        String newEmail = accountEmailService.validateEmailChange(patient.getEmail(), requestedEmail);
        if (newEmail != null && patient.getUser() != null) {
            patient.getUser().setEmail(newEmail);
        }

        return patientRepository.save(patient);
    }

    public Patient getPatientProfile(String username) {
        return patientRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
    }

    // Medical History Management
    @CacheEvict(value = "PATIENT:HISTORY", key = "'history_' + #patientId")
    public MedicalHistory addMedicalHistory(Long patientId, MedicalHistory medicalHistory) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        medicalHistory.setPatient(patient);
        return medicalHistoryRepository.save(medicalHistory);
    }

    @Cacheable(value = "PATIENT:HISTORY", key = "'history_' + #patientId")
    public List<MedicalHistoryDto> getPatientMedicalHistoryDto(Long patientId) {
        return medicalHistoryRepository.findByPatientId(patientId).stream()
                .map(MedicalHistoryDto::new)
                .toList();
    }

    public List<MedicalHistory> getPatientMedicalHistory(Long patientId) {
        return medicalHistoryRepository.findByPatientId(patientId);
    }

    @CacheEvict(value = "PATIENT:HISTORY", allEntries = true)
    public MedicalHistory updateMedicalHistory(Long historyId, MedicalHistory updatedHistory) {
        // Same rules as /api/medical-history, including the signed-record lock
        return medicalHistoryService.updateMedicalHistory(historyId, updatedHistory);
    }

    @CacheEvict(value = "PATIENT:HISTORY", allEntries = true)
    public void deleteMedicalHistory(Long historyId) {
        medicalHistoryService.deleteMedicalHistory(historyId);
    }

    public List<MedicalHistory> getMedicalHistoryByDateRange(Long patientId, LocalDate startDate, LocalDate endDate) {
        // Inclusive range in SQL instead of loading and decrypting the whole history
        return medicalHistoryRepository.findByPatientIdAndVisitDateBetween(patientId, startDate, endDate);
    }

    public List<Patient> getPatientsByIllness(String illnessKeyword, java.util.Collection<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return List.of();
        }
        // illness_details is encrypted, so matching must happen after decryption; limit it to allowed patients
        return patientRepository.findAllById(patientIds).stream()
                .filter(patient -> patient.getIllnessDetails() != null &&
                        patient.getIllnessDetails().toLowerCase().contains(illnessKeyword.toLowerCase()))
                .toList();
    }

    @CacheEvict(value = "PATIENT:PROFILE", allEntries = true)
    public Patient updateIllnessDetails(Long patientId, String illnessDetails) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setIllnessDetails(illnessDetails);
        return patientRepository.save(patient);
    }

    @CacheEvict(value = "PATIENT:PROFILE", allEntries = true)
    public Patient updateContactInfo(Long patientId, String contactInfo) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setContactInfo(contactInfo);
        return patientRepository.save(patient);
    }

    public Patient updateProfileImage(Long patientId, String imageUrl) {
        return setProfileImage(patientRepository.findById(patientId), imageUrl);
    }

    public Patient updateProfileImageByUsername(String username, String imageUrl) {
        return setProfileImage(patientRepository.findByUsername(username), imageUrl);
    }

    private Patient setProfileImage(Optional<Patient> found, String imageUrl) {
        Patient patient = found.orElseThrow(() -> new RuntimeException("Patient not found"));
        patient.setProfileImageUrl(imageUrl);
        patient.setUpdatedAt(LocalDateTime.now());
        return patientRepository.save(patient);
    }

    private static <T> void setIfPresent(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}