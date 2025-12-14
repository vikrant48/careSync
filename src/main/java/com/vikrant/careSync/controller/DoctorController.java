package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Experience;
import com.vikrant.careSync.entity.Education;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.dto.CreateEducationRequest;
import com.vikrant.careSync.dto.CreateExperienceRequest;
import com.vikrant.careSync.dto.CreateCertificateRequest;
import com.vikrant.careSync.dto.UpdateDoctorRequest;
import com.vikrant.careSync.service.interfaces.IDoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class DoctorController {

    private final IDoctorService doctorService;

    @GetMapping
    public ResponseEntity<List<Doctor>> getAllDoctors() {
        List<Doctor> doctors = doctorService.getAllDoctors();
        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/for-patients")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<Doctor>> getAllDoctorsForPatients() {
        List<Doctor> doctors = doctorService.getAllDoctors();
        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/public/{username}")
    public ResponseEntity<Doctor> getDoctorByUsername(@PathVariable String username) {
        Optional<Doctor> doctor = doctorService.getDoctorByUsername(username);
        return doctor.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/profile/{username}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Doctor> getDoctorProfile(@PathVariable String username) {
        try {
            Doctor doctor = doctorService.getDoctorProfile(username);
            return ResponseEntity.ok(doctor);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/profile/{username}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Doctor> updateDoctorProfile(@PathVariable String username,
            @RequestBody UpdateDoctorRequest request) {
        try {
            Doctor doctor = doctorService.updateDoctorProfileByUsername(username, request);
            return ResponseEntity.ok(doctor);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/profile/{username}/image")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Doctor> updateProfileImage(@PathVariable String username, @RequestParam String imageUrl) {
        try {
            Doctor doctor = doctorService.updateProfileImageByUsername(username, imageUrl);
            return ResponseEntity.ok(doctor);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/specialization/{specialization}")
    public ResponseEntity<List<Doctor>> getDoctorsBySpecialization(@PathVariable String specialization) {
        List<Doctor> doctors = doctorService.getDoctorsBySpecialization(specialization);
        return ResponseEntity.ok(doctors);
    }

    // Experience Management
    @PostMapping("/profile/{username}/experiences")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Experience> addExperience(@PathVariable String username,
            @RequestBody CreateExperienceRequest request) {
        try {
            Experience experience = new Experience();
            experience.setHospitalName(request.getHospitalName());
            experience.setPosition(request.getPosition());
            experience.setYearsOfService(request.getYearsOfService());
            experience.setDetails(request.getDetails());

            Experience savedExperience = doctorService.addExperienceByUsername(username, experience);
            return ResponseEntity.ok(savedExperience);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/profile/{username}/experiences")
    public ResponseEntity<List<Experience>> getDoctorExperiences(@PathVariable String username) {
        List<Experience> experiences = doctorService.getDoctorExperiencesByUsername(username);
        return ResponseEntity.ok(experiences);
    }

    @PutMapping("/profile/{username}/experiences/{experienceId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Experience> updateExperience(@PathVariable String username, @PathVariable Long experienceId,
            @RequestBody CreateExperienceRequest request) {
        try {
            Experience experience = new Experience();
            experience.setHospitalName(request.getHospitalName());
            experience.setPosition(request.getPosition());
            experience.setYearsOfService(request.getYearsOfService());
            experience.setDetails(request.getDetails());

            Experience updatedExperience = doctorService.updateExperience(experienceId, experience);
            return ResponseEntity.ok(updatedExperience);
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
    public ResponseEntity<Education> addEducation(@PathVariable String username,
            @RequestBody CreateEducationRequest request) {
        try {
            Education education = new Education();
            education.setDegree(request.getDegree());
            education.setInstitution(request.getInstitution());
            education.setYearOfCompletion(request.getYearOfCompletion());
            education.setDetails(request.getDetails());

            Education savedEducation = doctorService.addEducationByUsername(username, education);
            return ResponseEntity.ok(savedEducation);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/profile/{username}/educations")
    public ResponseEntity<List<Education>> getDoctorEducations(@PathVariable String username) {
        List<Education> educations = doctorService.getDoctorEducationsByUsername(username);
        return ResponseEntity.ok(educations);
    }

    @PutMapping("/profile/{username}/educations/{educationId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Education> updateEducation(@PathVariable String username, @PathVariable Long educationId,
            @RequestBody CreateEducationRequest request) {
        try {
            Education education = new Education();
            education.setDegree(request.getDegree());
            education.setInstitution(request.getInstitution());
            education.setYearOfCompletion(request.getYearOfCompletion());
            education.setDetails(request.getDetails());

            Education updatedEducation = doctorService.updateEducation(educationId, education);
            return ResponseEntity.ok(updatedEducation);
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
    public ResponseEntity<Certificate> addCertificate(@PathVariable String username,
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
            return ResponseEntity.ok(savedCertificate);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/profile/{username}/certificates")
    public ResponseEntity<List<Certificate>> getDoctorCertificates(@PathVariable String username) {
        List<Certificate> certificates = doctorService.getDoctorCertificatesByUsername(username);
        return ResponseEntity.ok(certificates);
    }

    @PutMapping("/profile/{username}/certificates/{certificateId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Certificate> updateCertificate(@PathVariable String username,
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
            return ResponseEntity.ok(updatedCertificate);
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