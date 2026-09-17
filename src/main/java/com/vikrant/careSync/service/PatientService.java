package com.vikrant.careSync.service;

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

    @Override
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @Override
    public long getPatientCount() {
        try {
            Cache cache = cacheManager.getCache("PATIENT:PAGINATED_LIST");
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get("total_patient_count");
                if (wrapper != null && wrapper.get() instanceof Long count) {
                    return count;
                }
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for patient count fetch: {}. Falling back to DB.", e.getMessage());
        }

        long count = patientRepository.count();

        try {
            Cache cache = cacheManager.getCache("PATIENT:PAGINATED_LIST");
            if (cache != null) {
                cache.put("total_patient_count", count);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for patient count write: {}", e.getMessage());
        }

        return count;
    }

    @Override
    public List<PatientDto> getPatientsPaginated(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        Cache cache = null;
        try {
            cache = cacheManager.getCache("PATIENT:PAGINATED_LIST");
        } catch (Exception e) {
            log.warn("Redis cache manager error: {}", e.getMessage());
        }

        String targetKey = "page_" + safePage + "_size_" + safeSize;

        // 1. Try reading requested page from Redis
        if (cache != null) {
            try {
                Cache.ValueWrapper wrapper = cache.get(targetKey);
                if (wrapper != null && wrapper.get() instanceof List<?> rawList) {
                    @SuppressWarnings("unchecked")
                    List<PatientDto> cachedDtos = (List<PatientDto>) rawList;
                    log.info("Redis HIT for paginated patients key [{}]", targetKey);

                    // Asynchronously/in background prefetch adjacent window pages
                    prefetchWindowPages(safePage, safeSize, cache);
                    return cachedDtos;
                }
            } catch (Exception e) {
                log.warn("Redis error reading key [{}]: {}. Falling back to DB.", targetKey, e.getMessage());
            }
        }

        log.info("Redis MISS for paginated patients key [{}]. Fetching from DB...", targetKey);
        List<PatientDto> dtos = fetchPageFromDb(safePage, safeSize);

        // Save target page to Redis
        if (cache != null) {
            try {
                cache.put(targetKey, dtos);
            } catch (Exception e) {
                log.warn("Redis error writing key [{}]: {}", targetKey, e.getMessage());
            }
        }

        // Prefetch adjacent window pages (Page 1 / Page P+1 and P-1)
        prefetchWindowPages(safePage, safeSize, cache);

        return dtos;
    }

    private List<PatientDto> fetchPageFromDb(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return patientRepository.findAll(pageable).stream()
                .map(this::convertToDtoWithStats)
                .toList();
    }

    private void prefetchWindowPages(int currentPage, int pageSize, Cache cache) {
        if (cache == null)
            return;

        // Determine window pages: Initial load (page 0) prefetches page 1 (100 total
        // items).
        // Navigation to page P prefetches page P+1 and page P-1 (sliding window).
        int[] windowPages = (currentPage == 0)
                ? new int[] { 1 }
                : new int[] { currentPage - 1, currentPage + 1 };

        for (int p : windowPages) {
            if (p < 0)
                continue;
            String key = "page_" + p + "_size_" + pageSize;
            try {
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper == null) {
                    List<PatientDto> pageDtos = fetchPageFromDb(p, pageSize);
                    if (!pageDtos.isEmpty()) {
                        cache.put(key, pageDtos);
                        log.info("Prefetched and cached adjacent page [{}] into Redis.", key);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to prefetch window page [{}]: {}", key, e.getMessage());
            }
        }
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
        int percentage = 0;

        // Basic Info (30%)
        if (patient.getFirstName() != null && !patient.getFirstName().isEmpty() &&
                patient.getLastName() != null && !patient.getLastName().isEmpty() &&
                patient.getEmail() != null && !patient.getEmail().isEmpty() &&
                patient.getContactInfo() != null && !patient.getContactInfo().isEmpty()) {
            percentage += 30;
        }

        // Personal Details (30%)
        if (patient.getDateOfBirth() != null &&
                patient.getGender() != null && !patient.getGender().isEmpty() &&
                patient.getBloodGroup() != null && !patient.getBloodGroup().isEmpty()) {
            percentage += 30;
        }

        // Profile Image (20%)
        if (patient.getProfileImageUrl() != null && !patient.getProfileImageUrl().isEmpty()) {
            percentage += 20;
        }

        // Medical Context (20%)
        if (patient.getIllnessDetails() != null && !patient.getIllnessDetails().isEmpty()) {
            percentage += 20;
        }

        return percentage;
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

        if (updatedPatient.getFirstName() != null) {
            patient.setFirstName(updatedPatient.getFirstName());
        }
        if (updatedPatient.getLastName() != null) {
            patient.setLastName(updatedPatient.getLastName());
        }
        if (updatedPatient.getDateOfBirth() != null) {
            patient.setDateOfBirth(updatedPatient.getDateOfBirth());
        }
        if (updatedPatient.getContactInfo() != null) {
            patient.setContactInfo(updatedPatient.getContactInfo());
        }
        if (updatedPatient.getIllnessDetails() != null) {
            patient.setIllnessDetails(updatedPatient.getIllnessDetails());
        }
        if (updatedPatient.getGender() != null) {
            patient.setGender(updatedPatient.getGender());
        }
        if (updatedPatient.getBloodGroup() != null) {
            patient.setBloodGroup(updatedPatient.getBloodGroup());
        }
        if (updatedPatient.getIsActive() != null) {
            patient.setIsActive(updatedPatient.getIsActive());
        }

        // Email update handling:
        // Safely extract email from attached user object or patient delegate without
        // overwriting with null
        String requestedEmail = (updatedPatient.getUser() != null && updatedPatient.getUser().getEmail() != null)
                ? updatedPatient.getUser().getEmail()
                : updatedPatient.getEmail();

        if (requestedEmail != null && !requestedEmail.trim().isEmpty()) {
            if (patient.getUser() != null) {
                patient.getUser().setEmail(requestedEmail);
            }
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