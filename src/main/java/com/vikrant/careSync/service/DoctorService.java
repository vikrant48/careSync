package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Experience;
import com.vikrant.careSync.entity.Education;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.dto.*;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.ExperienceRepository;
import com.vikrant.careSync.repository.EducationRepository;
import com.vikrant.careSync.repository.CertificateRepository;
import com.vikrant.careSync.repository.FeedbackRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final ExperienceRepository experienceRepository;
    private final EducationRepository educationRepository;
    private final CertificateRepository certificateRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackService feedbackService;
    private final CacheManager cacheManager;

    @Cacheable(value = "DOCTOR:PROFILE", key = "'all'")
    public List<DoctorDto> getAllDoctorsDto() {
        List<Doctor> doctors = doctorRepository.findAll();
        return convertDoctorsToDtosWithStats(doctors);
    }

    public List<DoctorDto> convertDoctorsToDtosWithStats(List<Doctor> doctors) {
        if (doctors == null || doctors.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> doctorIds = doctors.stream().map(Doctor::getId).toList();

        // 1 Batch Query for Average Ratings and Review Counts
        List<Object[]> ratingAggregates = feedbackRepository.findDoctorRatingAggregatesIn(doctorIds);
        Map<Long, Double> avgRatingMap = new HashMap<>();
        Map<Long, Long> reviewCountMap = new HashMap<>();
        if (ratingAggregates != null) {
            for (Object[] row : ratingAggregates) {
                Long docId = (Long) row[0];
                Double avgRating = (Double) row[1];
                Long count = (Long) row[2];
                avgRatingMap.put(docId, avgRating != null ? avgRating : 0.0);
                reviewCountMap.put(docId, count != null ? count : 0L);
            }
        }

        // 1 Batch Query for doctors with experiences
        Set<Long> docsWithExp = new HashSet<>(experienceRepository.findDoctorIdsWithExperienceIn(doctorIds));

        // 1 Batch Query for doctors with education
        Set<Long> docsWithEdu = new HashSet<>(educationRepository.findDoctorIdsWithEducationIn(doctorIds));

        return doctors.stream().map(doctor -> {
            DoctorDto dto = new DoctorDto(doctor);
            dto.setAverageRating(avgRatingMap.getOrDefault(doctor.getId(), 0.0));
            dto.setReviewCount(reviewCountMap.getOrDefault(doctor.getId(), 0L));
            dto.setCompletionPercentage(calculateCompletionPercentageFast(doctor, docsWithExp.contains(doctor.getId()),
                    docsWithEdu.contains(doctor.getId())));
            return dto;
        }).collect(Collectors.toList());
    }

    private DoctorDto convertToDtoWithStats(Doctor doctor) {
        if (doctor == null)
            return null;
        List<DoctorDto> list = convertDoctorsToDtosWithStats(Collections.singletonList(doctor));
        return list.isEmpty() ? null : list.get(0);
    }

    private int calculateCompletionPercentageFast(Doctor doctor, boolean hasExperience, boolean hasEducation) {
        int percentage = 0;

        // Basic Info (20%)
        if (doctor.getFirstName() != null && !doctor.getFirstName().isEmpty() &&
                doctor.getLastName() != null && !doctor.getLastName().isEmpty() &&
                doctor.getEmail() != null && !doctor.getEmail().isEmpty() &&
                doctor.getContactInfo() != null && !doctor.getContactInfo().isEmpty() &&
                doctor.getGender() != null && !doctor.getGender().isEmpty()) {
            percentage += 20;
        }

        // Professional Details (20%)
        if (doctor.getSpecialization() != null && !doctor.getSpecialization().isEmpty() &&
                doctor.getConsultationFees() != null &&
                doctor.getLanguages() != null && !doctor.getLanguages().isEmpty()) {
            percentage += 20;
        }

        // Address & Bio (10%)
        if (doctor.getAddress() != null && !doctor.getAddress().isEmpty()) {
            percentage += 10;
        }

        // Profile Image (10%)
        if (doctor.getProfileImageUrl() != null && !doctor.getProfileImageUrl().isEmpty()) {
            percentage += 10;
        }

        // Experience (20%)
        if (hasExperience) {
            percentage += 20;
        }

        // Education (20%)
        if (hasEducation) {
            percentage += 20;
        }

        return percentage;
    }

    private int calculateCompletionPercentage(Doctor doctor) {
        return calculateCompletionPercentageFast(
                doctor,
                !experienceRepository.findByDoctorId(doctor.getId()).isEmpty(),
                !educationRepository.findByDoctorId(doctor.getId()).isEmpty());
    }

    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    @Cacheable(value = "DOCTOR:PROFILE", key = "'id_' + #id")
    public Optional<DoctorDto> getDoctorDtoById(Long id) {
        return doctorRepository.findById(id).map(this::convertToDtoWithStats);
    }

    @Cacheable(value = "DOCTOR:PROFILE", key = "'username_' + #username")
    public Optional<DoctorDto> getDoctorDtoByUsername(String username) {
        return doctorRepository.findByUsername(username).map(this::convertToDtoWithStats);
    }

    public Optional<Doctor> getDoctorById(Long id) {
        return doctorRepository.findById(id);
    }

    public Optional<Doctor> getDoctorByUsername(String username) {
        return doctorRepository.findByUsername(username);
    }

    @CacheEvict(value = "DOCTOR:PROFILE", allEntries = true)
    public Doctor updateDoctorProfile(Long doctorId, Doctor updatedDoctor) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setFirstName(updatedDoctor.getFirstName());
        doctor.setLastName(updatedDoctor.getLastName());
        doctor.setSpecialization(updatedDoctor.getSpecialization());
        doctor.setProfileImageUrl(updatedDoctor.getProfileImageUrl());

        return doctorRepository.save(doctor);
    }

    @CacheEvict(value = "DOCTOR:PROFILE", allEntries = true)
    public Doctor updateDoctorProfileByUsername(String username, UpdateDoctorRequest request) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (request.getFirstName() != null)
            doctor.setFirstName(request.getFirstName());
        if (request.getLastName() != null)
            doctor.setLastName(request.getLastName());
        if (request.getSpecialization() != null)
            doctor.setSpecialization(request.getSpecialization());
        if (request.getContactInfo() != null)
            doctor.setContactInfo(request.getContactInfo());
        if (request.getEmail() != null)
            doctor.setEmail(request.getEmail());
        if (request.getIsActive() != null)
            doctor.setIsActive(request.getIsActive());
        if (request.getGender() != null)
            doctor.setGender(request.getGender());
        if (request.getConsultationFees() != null)
            doctor.setConsultationFees(java.math.BigDecimal.valueOf(request.getConsultationFees()));
        if (request.getAddress() != null)
            doctor.setAddress(request.getAddress());
        if (request.getLanguages() != null)
            doctor.setLanguages(String.join(",", request.getLanguages()));

        doctor.setUpdatedAt(LocalDateTime.now());

        return doctorRepository.save(doctor);
    }

    @CacheEvict(value = "DOCTOR:PROFILE", allEntries = true)
    public Doctor updateProfileImage(Long doctorId, String imageUrl) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setProfileImageUrl(imageUrl);
        return doctorRepository.save(doctor);
    }

    @CacheEvict(value = "DOCTOR:PROFILE", allEntries = true)
    public Doctor updateProfileImageByUsername(String username, String imageUrl) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setProfileImageUrl(imageUrl);
        doctor.setUpdatedAt(LocalDateTime.now());
        return doctorRepository.save(doctor);
    }

    // Experience Management
    @CacheEvict(value = "doctorListing", allEntries = true)
    public Experience addExperience(Long doctorId, Experience experience) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        experience.setDoctor(doctor);
        return experienceRepository.save(experience);
    }

    @CacheEvict(value = "DOCTOR:EXPERIENCE", allEntries = true)
    public Experience addExperienceByUsername(String username, Experience experience) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        experience.setDoctor(doctor);
        return experienceRepository.save(experience);
    }

    @Cacheable(value = "DOCTOR:EXPERIENCE", key = "'experience_' + #doctorId")
    public List<ExperienceDto> getDoctorExperiencesDto(Long doctorId) {
        return experienceRepository.findByDoctorId(doctorId).stream()
                .map(ExperienceDto::new)
                .toList();
    }

    public List<Experience> getDoctorExperiences(Long doctorId) {
        return experienceRepository.findByDoctorId(doctorId);
    }

    public List<Experience> getDoctorExperiencesByUsername(String username) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        return experienceRepository.findByDoctorId(doctor.getId());
    }

    @CacheEvict(value = "DOCTOR:EXPERIENCE", allEntries = true)
    public Experience updateExperience(Long experienceId, Experience updatedExperience) {
        Experience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new RuntimeException("Experience not found"));

        experience.setHospitalName(updatedExperience.getHospitalName());
        experience.setPosition(updatedExperience.getPosition());
        experience.setYearsOfService(updatedExperience.getYearsOfService());
        experience.setDetails(updatedExperience.getDetails());

        return experienceRepository.save(experience);
    }

    @CacheEvict(value = "DOCTOR:EXPERIENCE", allEntries = true)
    public void deleteExperience(Long experienceId) {
        experienceRepository.deleteById(experienceId);
    }

    // Education Management
    @CacheEvict(value = "doctorListing", allEntries = true)
    public Education addEducation(Long doctorId, Education education) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        education.setDoctor(doctor);
        return educationRepository.save(education);
    }

    @CacheEvict(value = "DOCTOR:EDUCATION", allEntries = true)
    public Education addEducationByUsername(String username, Education education) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        education.setDoctor(doctor);
        return educationRepository.save(education);
    }

    @Cacheable(value = "DOCTOR:EDUCATION", key = "'education_' + #doctorId")
    public List<EducationDto> getDoctorEducationsDto(Long doctorId) {
        return educationRepository.findByDoctorId(doctorId).stream()
                .map(EducationDto::new)
                .toList();
    }

    public List<Education> getDoctorEducations(Long doctorId) {
        return educationRepository.findByDoctorId(doctorId);
    }

    public List<Education> getDoctorEducationsByUsername(String username) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        return educationRepository.findByDoctorId(doctor.getId());
    }

    @CacheEvict(value = "DOCTOR:EDUCATION", allEntries = true)
    public Education updateEducation(Long educationId, Education updatedEducation) {
        Education education = educationRepository.findById(educationId)
                .orElseThrow(() -> new RuntimeException("Education not found"));

        education.setDegree(updatedEducation.getDegree());
        education.setInstitution(updatedEducation.getInstitution());
        education.setYearOfCompletion(updatedEducation.getYearOfCompletion());
        education.setDetails(updatedEducation.getDetails());

        return educationRepository.save(education);
    }

    @CacheEvict(value = "DOCTOR:EDUCATION", allEntries = true)
    public void deleteEducation(Long educationId) {
        educationRepository.deleteById(educationId);
    }

    // Certificate Management
    @CacheEvict(value = "doctorListing", allEntries = true)
    public Certificate addCertificate(Long doctorId, Certificate certificate) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        certificate.setDoctor(doctor);
        return certificateRepository.save(certificate);
    }

    @CacheEvict(value = "DOCTOR:CERTIFICATES", allEntries = true)
    public Certificate addCertificateByUsername(String username, Certificate certificate) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        certificate.setDoctor(doctor);
        return certificateRepository.save(certificate);
    }

    @Cacheable(value = "DOCTOR:CERTIFICATES", key = "'certificate_' + #doctorId")
    public List<CertificateDto> getDoctorCertificatesDto(Long doctorId) {
        return certificateRepository.findByDoctorId(doctorId).stream()
                .map(CertificateDto::new)
                .toList();
    }

    public List<Certificate> getDoctorCertificates(Long doctorId) {
        return certificateRepository.findByDoctorId(doctorId);
    }

    public List<Certificate> getDoctorCertificatesByUsername(String username) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        return certificateRepository.findByDoctorId(doctor.getId());
    }

    @CacheEvict(value = "DOCTOR:CERTIFICATES", allEntries = true)
    public Certificate updateCertificate(Long certificateId, Certificate updatedCertificate) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));

        certificate.setName(updatedCertificate.getName());
        certificate.setUrl(updatedCertificate.getUrl());
        certificate.setDetails(updatedCertificate.getDetails());
        certificate.setIssuingOrganization(updatedCertificate.getIssuingOrganization());
        certificate.setIssueDate(updatedCertificate.getIssueDate());
        certificate.setExpiryDate(updatedCertificate.getExpiryDate());
        certificate.setCredentialId(updatedCertificate.getCredentialId());
        certificate.setCredentialUrl(updatedCertificate.getCredentialUrl());

        return certificateRepository.save(certificate);
    }

    @CacheEvict(value = "DOCTOR:CERTIFICATES", allEntries = true)
    public Certificate updateCertificateUrl(Long certificateId, String url) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));

        certificate.setUrl(url);
        return certificateRepository.save(certificate);
    }

    @CacheEvict(value = "DOCTOR:CERTIFICATES", allEntries = true)
    public void deleteCertificate(Long certificateId) {
        certificateRepository.deleteById(certificateId);
    }

    public List<Doctor> getDoctorsBySpecialization(String specialization) {
        SearchRequestDto searchDto = SearchRequestDto.builder()
                .specialization(specialization)
                .build();
        return doctorRepository.searchDoctorsDynamic(searchDto);
    }

    public List<DoctorDto> searchDoctors(SearchRequestDto searchDto) {
        int page = searchDto != null && searchDto.getPage() != null ? Math.max(0, searchDto.getPage()) : 0;
        int size = searchDto != null && searchDto.getSize() != null ? Math.min(Math.max(1, searchDto.getSize()), 100)
                : 50;

        String queryKey = (searchDto != null ? Objects.toString(searchDto.getQuery(), "") : "") + "_" +
                (searchDto != null ? Objects.toString(searchDto.getSpecialization(), "") : "") + "_" +
                (searchDto != null ? Objects.toString(searchDto.getLocation(), "") : "");

        Cache cache = null;
        try {
            cache = cacheManager.getCache("DOCTOR:PAGINATED_LIST");
        } catch (Exception e) {
            log.warn("Redis CacheManager error for doctors: {}", e.getMessage());
        }

        String cacheKey = "query_" + queryKey + "_page_" + page + "_size_" + size;

        if (cache != null) {
            try {
                Cache.ValueWrapper wrapper = cache.get(cacheKey);
                if (wrapper != null && wrapper.get() instanceof List<?> rawList) {
                    @SuppressWarnings("unchecked")
                    List<DoctorDto> cachedList = (List<DoctorDto>) rawList;
                    log.info("Redis HIT for paginated doctors key [{}]", cacheKey);
                    prefetchDoctorWindowPages(searchDto, page, size, cache, queryKey);
                    return cachedList;
                }
            } catch (Exception e) {
                log.warn("Redis error reading doctor cache key [{}]: {}. Falling back to DB.", cacheKey,
                        e.getMessage());
            }
        }

        log.info("Redis MISS for paginated doctors key [{}]. Fetching from DB...", cacheKey);
        List<DoctorDto> dtos = fetchDoctorPageFromDb(searchDto, page, size);

        if (cache != null) {
            try {
                cache.put(cacheKey, dtos);
            } catch (Exception e) {
                log.warn("Redis error writing doctor cache key [{}]: {}", cacheKey, e.getMessage());
            }
        }

        prefetchDoctorWindowPages(searchDto, page, size, cache, queryKey);
        return dtos;
    }

    private List<DoctorDto> fetchDoctorPageFromDb(SearchRequestDto baseSearchDto, int page, int size) {
        SearchRequestDto pageReq = SearchRequestDto.builder()
                .query(baseSearchDto != null ? baseSearchDto.getQuery() : null)
                .specialization(baseSearchDto != null ? baseSearchDto.getSpecialization() : null)
                .location(baseSearchDto != null ? baseSearchDto.getLocation() : null)
                .gender(baseSearchDto != null ? baseSearchDto.getGender() : null)
                .page(page)
                .size(size)
                .sortBy(baseSearchDto != null ? baseSearchDto.getSortBy() : null)
                .sortDirection(baseSearchDto != null ? baseSearchDto.getSortDirection() : null)
                .build();
        List<Doctor> doctors = doctorRepository.searchDoctorsDynamic(pageReq);
        return convertDoctorsToDtosWithStats(doctors);
    }

    private void prefetchDoctorWindowPages(SearchRequestDto searchDto, int currentPage, int pageSize, Cache cache,
            String queryKey) {
        if (cache == null)
            return;

        int[] windowPages = (currentPage == 0)
                ? new int[] { 1 }
                : new int[] { currentPage - 1, currentPage + 1 };

        for (int p : windowPages) {
            if (p < 0)
                continue;
            String key = "query_" + queryKey + "_page_" + p + "_size_" + pageSize;
            try {
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper == null) {
                    List<DoctorDto> pageDtos = fetchDoctorPageFromDb(searchDto, p, pageSize);
                    if (!pageDtos.isEmpty()) {
                        cache.put(key, pageDtos);
                        log.info("Prefetched and cached doctor window page [{}] into Redis.", key);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to prefetch doctor window page [{}]: {}", key, e.getMessage());
            }
        }
    }

    public long countDoctors(SearchRequestDto searchDto) {
        return doctorRepository.countDoctorsDynamic(searchDto);
    }

    public Doctor getDoctorProfile(String username) {
        return doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }
}