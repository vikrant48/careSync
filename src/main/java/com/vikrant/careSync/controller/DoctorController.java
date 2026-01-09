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
import com.vikrant.careSync.service.interfaces.IDoctorService;
import lombok.RequiredArgsConstructor;
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
public class DoctorController {

    private final IDoctorService doctorService;

    @GetMapping
    public ResponseEntity<List<DoctorDto>> getAllDoctors() {
        return ResponseEntity.ok(doctorService.getAllDoctorsDto());
    }

    @GetMapping("/for-patients")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<DoctorDto>> getAllDoctorsForPatients() {
        return ResponseEntity.ok(doctorService.getAllDoctorsDto());
    }

    @GetMapping("/public/{username}")
    public ResponseEntity<DoctorDto> getDoctorByUsername(@PathVariable String username) {
        return doctorService.getDoctorDtoByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/profile/{username}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorDto> getDoctorProfile(@PathVariable String username) {
        try {
            Doctor doctor = doctorService.getDoctorProfile(username);
            return ResponseEntity.ok(new DoctorDto(doctor));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/profile/{username}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorDto> updateDoctorProfile(@PathVariable String username,
            @RequestBody UpdateDoctorRequest request) {
        try {
            Doctor doctor = doctorService.updateDoctorProfileByUsername(username, request);
            return ResponseEntity.ok(new DoctorDto(doctor));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/profile/{username}/image")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorDto> updateProfileImage(@PathVariable String username, @RequestParam String imageUrl) {
        try {
            Doctor doctor = doctorService.updateProfileImageByUsername(username, imageUrl);
            return ResponseEntity.ok(new DoctorDto(doctor));
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
    public ResponseEntity<List<ExperienceDto>> getDoctorExperiences(@PathVariable String username) {
        List<ExperienceDto> experiences = doctorService.getDoctorExperiencesByUsername(username).stream()
                .map(ExperienceDto::new)
                .collect(Collectors.toList());
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
    public ResponseEntity<List<EducationDto>> getDoctorEducations(@PathVariable String username) {
        List<EducationDto> educations = doctorService.getDoctorEducationsByUsername(username).stream()
                .map(EducationDto::new)
                .collect(Collectors.toList());
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
    public ResponseEntity<List<CertificateDto>> getDoctorCertificates(@PathVariable String username) {
        List<CertificateDto> certificates = doctorService.getDoctorCertificatesByUsername(username).stream()
                .map(CertificateDto::new)
                .collect(Collectors.toList());
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