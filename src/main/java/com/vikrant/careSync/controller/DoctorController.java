package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Experience;
import com.vikrant.careSync.entity.Education;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.dto.CreateEducationRequest;
import com.vikrant.careSync.dto.CreateExperienceRequest;
import com.vikrant.careSync.dto.CreateCertificateRequest;
import com.vikrant.careSync.dto.UpdateDoctorRequest;
import com.vikrant.careSync.dto.DoctorDto;
import com.vikrant.careSync.dto.ExperienceDto;
import com.vikrant.careSync.dto.EducationDto;
import com.vikrant.careSync.dto.CertificateDto;
import com.vikrant.careSync.service.DoctorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Doctors", description = "Endpoints for doctor profile, experience, and education management")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class DoctorController {

    private final DoctorService doctorService;
    private final CacheManager cacheManager;

    @io.swagger.v3.oas.annotations.Operation(summary = "Get all doctors", description = "Retrieves a list of all registered doctors")
    @GetMapping
    public ResponseEntity<List<DoctorDto>> getAllDoctors() {
        return ResponseEntity.ok(doctorService.getAllDoctorsDto());
    }

    @GetMapping("/for-patients")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<List<DoctorDto>> getAllDoctorsForPatients() {
        return ResponseEntity.ok(doctorService.getAllDoctorsDto());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get public profile", description = "Retrieves a doctor's public profile by username (no auth required)")
    @GetMapping("/public/{username}")
    public ResponseEntity<DoctorDto> getDoctorByUsername(@PathVariable String username) {
        return doctorService.getDoctorDtoByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get doctor profile by ID", description = "Retrieves the full profile of a doctor by doctor ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<DoctorDto> getDoctorById(@PathVariable Long id) {
        return doctorService.getDoctorDtoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get doctor profile", description = "Retrieves the full profile of the doctor by username")
    @GetMapping("/profile/{username}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<DoctorDto> getDoctorProfile(@PathVariable String username) {
        Cache cache = cacheManager.getCache("DOCTOR:PROFILE");
        String key = "username_" + username;

        DoctorDto doctorDto = null;
        if (cache != null) {
            try {
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper != null) {
                    doctorDto = (DoctorDto) wrapper.get();
                }
            } catch (Exception e) {
                log.warn("Redis unavailable during doctor profile cache read for {}: {}", username, e.getMessage());
            }
        }

        if (doctorDto == null) {
            log.info("Cache miss for doctor profile username: {}. Fetching from database...", username);
            Optional<DoctorDto> doctorDtoOpt = doctorService.getDoctorDtoByUsername(username);
            if (doctorDtoOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            doctorDto = doctorDtoOpt.get();

            if (cache != null) {
                try {
                    cache.put(key, doctorDto);
                    log.info("Saved doctor profile username: {} to Redis cache.", username);
                } catch (Exception e) {
                    log.warn("Redis unavailable during doctor profile cache write for {}: {}", username,
                            e.getMessage());
                }
            }
        } else {
            log.info("Cache hit for doctor profile username: {} in Redis.", username);
        }

        return ResponseEntity.ok(doctorDto);
    }

    @PutMapping("/profile/{username}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorDto> updateDoctorProfile(@PathVariable String username,
            @RequestBody UpdateDoctorRequest request) {
        try {
            doctorService.updateDoctorProfileByUsername(username, request);
            return doctorService.getDoctorDtoByUsername(username)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/profile/{username}/image")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorDto> updateProfileImage(@PathVariable String username, @RequestParam String imageUrl) {
        try {
            doctorService.updateProfileImageByUsername(username, imageUrl);
            return doctorService.getDoctorDtoByUsername(username)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/specialization/{specialization}")
    public ResponseEntity<List<DoctorDto>> getDoctorsBySpecialization(@PathVariable String specialization) {
        List<DoctorDto> doctors = doctorService.getDoctorsBySpecialization(specialization).stream()
                .map(DoctorDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(doctors);
    }

    // Experience Management
    @PostMapping("/profile/{username}/experiences")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ExperienceDto> addExperience(@PathVariable String username,
            @RequestBody CreateExperienceRequest request) {
        try {
            Experience experience = new Experience();
            experience.setHospitalName(request.getHospitalName());
            experience.setPosition(request.getPosition());
            experience.setYearsOfService(request.getYearsOfService());
            experience.setDetails(request.getDetails());

            Experience savedExperience = doctorService.addExperienceByUsername(username, experience);
            return ResponseEntity.ok(new ExperienceDto(savedExperience));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/profile/{username}/experiences")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<List<ExperienceDto>> getDoctorExperiences(@PathVariable String username) {
        List<ExperienceDto> experiences = doctorService.getDoctorExperiencesByUsername(username).stream()
                .map(ExperienceDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(experiences);
    }

    @GetMapping("/{id}/experiences")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<List<ExperienceDto>> getDoctorExperiencesById(@PathVariable Long id) {
        List<ExperienceDto> experiences = doctorService.getDoctorExperiencesDto(id);
        return ResponseEntity.ok(experiences);
    }

    @PutMapping("/profile/{username}/experiences/{experienceId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ExperienceDto> updateExperience(@PathVariable String username,
            @PathVariable Long experienceId,
            @RequestBody CreateExperienceRequest request) {
        try {
            Experience experience = new Experience();
            experience.setHospitalName(request.getHospitalName());
            experience.setPosition(request.getPosition());
            experience.setYearsOfService(request.getYearsOfService());
            experience.setDetails(request.getDetails());

            Experience updatedExperience = doctorService.updateExperience(experienceId, experience);
            return ResponseEntity.ok(new ExperienceDto(updatedExperience));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/profile/{username}/experiences/{experienceId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteExperience(@PathVariable String username, @PathVariable Long experienceId) {
        try {
            doctorService.deleteExperience(experienceId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Education Management
    @PostMapping("/profile/{username}/educations")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<EducationDto> addEducation(@PathVariable String username,
            @RequestBody CreateEducationRequest request) {
        try {
            Education education = new Education();
            education.setDegree(request.getDegree());
            education.setInstitution(request.getInstitution());
            education.setYearOfCompletion(request.getYearOfCompletion());
            education.setDetails(request.getDetails());

            Education savedEducation = doctorService.addEducationByUsername(username, education);
            return ResponseEntity.ok(new EducationDto(savedEducation));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/profile/{username}/educations")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<List<EducationDto>> getDoctorEducations(@PathVariable String username) {
        List<EducationDto> educations = doctorService.getDoctorEducationsByUsername(username).stream()
                .map(EducationDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(educations);
    }

    @GetMapping("/{id}/educations")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<List<EducationDto>> getDoctorEducationsById(@PathVariable Long id) {
        List<EducationDto> educations = doctorService.getDoctorEducationsDto(id);
        return ResponseEntity.ok(educations);
    }

    @PutMapping("/profile/{username}/educations/{educationId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<EducationDto> updateEducation(@PathVariable String username, @PathVariable Long educationId,
            @RequestBody CreateEducationRequest request) {
        try {
            Education education = new Education();
            education.setDegree(request.getDegree());
            education.setInstitution(request.getInstitution());
            education.setYearOfCompletion(request.getYearOfCompletion());
            education.setDetails(request.getDetails());

            Education updatedEducation = doctorService.updateEducation(educationId, education);
            return ResponseEntity.ok(new EducationDto(updatedEducation));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/profile/{username}/educations/{educationId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteEducation(@PathVariable String username, @PathVariable Long educationId) {
        try {
            doctorService.deleteEducation(educationId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Certificate Management
    @PostMapping("/profile/{username}/certificates")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<CertificateDto> addCertificate(@PathVariable String username,
            @RequestBody CreateCertificateRequest request) {
        try {
            Certificate certificate = new Certificate();
            certificate.setName(request.getName());
            certificate.setUrl(request.getUrl());
            certificate.setDetails(request.getDetails());
            certificate.setIssuingOrganization(request.getIssuingOrganization());
            certificate.setIssueDate(request.getIssueDate());
            certificate.setExpiryDate(request.getExpiryDate());
            certificate.setCredentialId(request.getCredentialId());
            certificate.setCredentialUrl(request.getCredentialUrl());

            Certificate savedCertificate = doctorService.addCertificateByUsername(username, certificate);
            return ResponseEntity.ok(new CertificateDto(savedCertificate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/profile/{username}/certificates")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<List<CertificateDto>> getDoctorCertificates(@PathVariable String username) {
        List<CertificateDto> certificates = doctorService.getDoctorCertificatesByUsername(username).stream()
                .map(CertificateDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(certificates);
    }

    @GetMapping("/{id}/certificates")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<List<CertificateDto>> getDoctorCertificatesById(@PathVariable Long id) {
        List<CertificateDto> certificates = doctorService.getDoctorCertificatesDto(id);
        return ResponseEntity.ok(certificates);
    }

    @PutMapping("/profile/{username}/certificates/{certificateId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<CertificateDto> updateCertificate(@PathVariable String username,
            @PathVariable Long certificateId, @RequestBody CreateCertificateRequest request) {
        try {
            Certificate certificate = new Certificate();
            certificate.setName(request.getName());
            certificate.setUrl(request.getUrl());
            certificate.setDetails(request.getDetails());
            certificate.setIssuingOrganization(request.getIssuingOrganization());
            certificate.setIssueDate(request.getIssueDate());
            certificate.setExpiryDate(request.getExpiryDate());
            certificate.setCredentialId(request.getCredentialId());
            certificate.setCredentialUrl(request.getCredentialUrl());

            Certificate updatedCertificate = doctorService.updateCertificate(certificateId, certificate);
            return ResponseEntity.ok(new CertificateDto(updatedCertificate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/profile/{username}/certificates/{certificateId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteCertificate(@PathVariable String username, @PathVariable Long certificateId) {
        try {
            doctorService.deleteCertificate(certificateId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}