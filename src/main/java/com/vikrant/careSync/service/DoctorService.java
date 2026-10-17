package com.vikrant.careSync.service;

import com.vikrant.careSync.util.ProfileCompletion;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final CacheManager cacheManager;
    private final PageCache pageCache;
    private final AccountEmailService accountEmailService;

    public AdminDoctorPagedResponse getAdminDoctorList(int page, int size, String search, Boolean isVerified) {
        int pageNumber = Math.max(0, page);
        int pageSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<AdminDoctorListItemDto> pagedList = doctorRepository.findAdminDoctorList(search, isVerified, pageable);
        long totalVerifiedCount = doctorRepository.countByIsVerified(true);
        long totalPendingCount = doctorRepository.countByIsVerified(false);
        return AdminDoctorPagedResponse.fromPage(pagedList, totalVerifiedCount, totalPendingCount);
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

        // 1 Batch Query for Experiences
        List<Experience> allExperiences = experienceRepository.findByDoctorIdIn(doctorIds);
        Map<Long, List<ExperienceDto>> expMap = allExperiences.stream()
                .filter(e -> e.getDoctor() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getDoctor().getId(),
                        Collectors.mapping(ExperienceDto::new, Collectors.toList())));

        // 1 Batch Query for Educations
        List<Education> allEducations = educationRepository.findByDoctorIdIn(doctorIds);
        Map<Long, List<EducationDto>> eduMap = allEducations.stream()
                .filter(e -> e.getDoctor() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getDoctor().getId(),
                        Collectors.mapping(EducationDto::new, Collectors.toList())));

        // 1 Batch Query for Certificates
        List<Certificate> allCertificates = certificateRepository.findByDoctorIdIn(doctorIds);
        Map<Long, List<CertificateDto>> certMap = allCertificates.stream()
                .filter(c -> c.getDoctor() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getDoctor().getId(),
                        Collectors.mapping(CertificateDto::new, Collectors.toList())));

        return doctors.stream().map(doctor -> {
            DoctorDto dto = new DoctorDto(doctor, false);
            List<ExperienceDto> exps = expMap.getOrDefault(doctor.getId(), Collections.emptyList());
            List<EducationDto> edus = eduMap.getOrDefault(doctor.getId(), Collections.emptyList());
            List<CertificateDto> certs = certMap.getOrDefault(doctor.getId(), Collections.emptyList());

            dto.setExperiences(exps);
            dto.setEducations(edus);
            dto.setCertificates(certs);
            dto.setAverageRating(avgRatingMap.getOrDefault(doctor.getId(), 0.0));
            dto.setReviewCount(reviewCountMap.getOrDefault(doctor.getId(), 0L));
            dto.setCompletionPercentage(calculateCompletionPercentageFast(doctor, !exps.isEmpty(), !edus.isEmpty()));
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
        return new ProfileCompletion()
                // Basic Info (20%)
                .section(20, ProfileCompletion.filled(doctor.getFirstName(), doctor.getLastName(),
                        doctor.getEmail(), doctor.getContactInfo(), doctor.getGender()))
                // Professional Details (20%)
                .section(20, doctor.getConsultationFees() != null
                        && ProfileCompletion.filled(doctor.getSpecialization(), doctor.getLanguages()))
                // Address (10%)
                .section(10, ProfileCompletion.filled(doctor.getAddress()))
                // Profile Image (10%)
                .section(10, ProfileCompletion.filled(doctor.getProfileImageUrl()))
                // Experience (20%) and Education (20%)
                .section(20, hasExperience)
                .section(20, hasEducation)
                .total();
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
            doctor.setContactInfo(blankToNull(request.getContactInfo()));
        if (request.getAlternateContactInfo() != null)
            doctor.setAlternateContactInfo(blankToNull(request.getAlternateContactInfo()));
        String newEmail = accountEmailService.validateEmailChange(doctor.getEmail(), request.getEmail());
        if (newEmail != null)
            doctor.setEmail(newEmail);
        // isActive is not taken from a profile edit: only admins activate or block accounts (AccountStatusService)
        if (request.getGender() != null)
            doctor.setGender(blankToNull(request.getGender()));
        if (request.getDateOfBirth() != null)
            doctor.setDateOfBirth(request.getDateOfBirth());
        if (request.getConsultationFees() != null)
            doctor.setConsultationFees(java.math.BigDecimal.valueOf(request.getConsultationFees()));
        if (request.getAddress() != null)
            doctor.setAddress(blankToNull(request.getAddress()));
        if (request.getLicenseNumber() != null)
            doctor.setLicenseNumber(blankToNull(request.getLicenseNumber()));
        if (request.getBio() != null)
            doctor.setBio(blankToNull(request.getBio()));
        if (request.getLanguages() != null)
            doctor.setLanguages(String.join(",", request.getLanguages()));

        doctor.setUpdatedAt(LocalDateTime.now());

        return doctorRepository.save(doctor);
    }

    @CacheEvict(value = "DOCTOR:PROFILE", allEntries = true)
    public Doctor updateProfileImage(Long doctorId, String imageUrl) {
        return setProfileImage(doctorRepository.findById(doctorId), imageUrl);
    }

    @CacheEvict(value = "DOCTOR:PROFILE", allEntries = true)
    public Doctor updateProfileImageByUsername(String username, String imageUrl) {
        return setProfileImage(doctorRepository.findByUsername(username), imageUrl);
    }

    private Doctor setProfileImage(Optional<Doctor> found, String imageUrl) {
        Doctor doctor = found.orElseThrow(() -> new RuntimeException("Doctor not found"));
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

        // Every search field is part of the key (gender and sort order were missing, so two searches differing only
        // in those returned the same cached page)
        String cacheKey = "query_" + (searchDto == null ? "" : String.join("_",
                Objects.toString(searchDto.getQuery(), ""), Objects.toString(searchDto.getSpecialization(), ""),
                Objects.toString(searchDto.getLocation(), ""), Objects.toString(searchDto.getGender(), ""),
                Objects.toString(searchDto.getVerified(), ""), Objects.toString(searchDto.getSortBy(), ""), Objects.toString(searchDto.getSortDirection(), "")))
                + "_page_" + page + "_size_" + size;
        int finalPage = page;
        int finalSize = size;
        return pageCache.list("DOCTOR:PAGINATED_LIST", cacheKey,
                () -> fetchDoctorPageFromDb(searchDto, finalPage, finalSize));
    }

    private List<DoctorDto> fetchDoctorPageFromDb(SearchRequestDto baseSearchDto, int page, int size) {
        SearchRequestDto pageReq = SearchRequestDto.builder()
                .query(baseSearchDto != null ? baseSearchDto.getQuery() : null)
                .specialization(baseSearchDto != null ? baseSearchDto.getSpecialization() : null)
                .location(baseSearchDto != null ? baseSearchDto.getLocation() : null)
                .gender(baseSearchDto != null ? baseSearchDto.getGender() : null)
                .verified(baseSearchDto != null ? baseSearchDto.getVerified() : null)
                .page(page)
                .size(size)
                .sortBy(baseSearchDto != null ? baseSearchDto.getSortBy() : null)
                .sortDirection(baseSearchDto != null ? baseSearchDto.getSortDirection() : null)
                .build();
        List<Doctor> doctors = doctorRepository.searchDoctorsDynamic(pageReq);
        return convertDoctorsToDtosWithStats(doctors);
    }

    public long countDoctors(SearchRequestDto searchDto) {
        return doctorRepository.countDoctorsDynamic(searchDto);
    }

    public Doctor getDoctorProfile(String username) {
        return doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}