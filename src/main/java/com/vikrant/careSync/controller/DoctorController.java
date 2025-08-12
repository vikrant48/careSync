package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Experience;
import com.vikrant.careSync.entity.Education;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.dto.CreateEducationRequest;
import com.vikrant.careSync.dto.CreateExperienceRequest;
import com.vikrant.careSync.dto.CreateCertificateRequest;
import com.vikrant.careSync.dto.UpdateDoctorRequest;
import com.vikrant.careSync.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    public ResponseEntity<List<Doctor>> getAllDoctors() {
        List<Doctor> doctors = doctorService.getAllDoctors();
        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Doctor> getDoctorById(@PathVariable Long id) {
        Optional<Doctor> doctor = doctorService.getDoctorById(id);
        return doctor.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
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

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Doctor> updateDoctorProfile(@PathVariable Long id, @RequestBody UpdateDoctorRequest request) {
        try {
            Doctor updatedDoctor = new Doctor();
            updatedDoctor.setFirstName(request.getFirstName());
            updatedDoctor.setLastName(request.getLastName());
            updatedDoctor.setSpecialization(request.getSpecialization());
            updatedDoctor.setProfileImageUrl(request.getProfileImageUrl());
            updatedDoctor.setEmail(request.getEmail());
            updatedDoctor.setIsActive(request.getIsActive());
            
            Doctor doctor = doctorService.updateDoctorProfile(id, updatedDoctor);
            return ResponseEntity.ok(doctor);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/profile-image")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Doctor> updateProfileImage(@PathVariable Long id, @RequestParam String imageUrl) {
        try {
            Doctor doctor = doctorService.updateProfileImage(id, imageUrl);
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
    @PostMapping("/{doctorId}/experiences")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Experience> addExperience(@PathVariable Long doctorId, @RequestBody CreateExperienceRequest request) {
        try {
            Experience experience = new Experience();
            experience.setHospitalName(request.getHospitalName());
            experience.setPosition(request.getPosition());
            experience.setYearsOfService(request.getYearsOfService());
            experience.setDetails(request.getDetails());
            
            Experience savedExperience = doctorService.addExperience(doctorId, experience);
            return ResponseEntity.ok(savedExperience);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{doctorId}/experiences")
    public ResponseEntity<List<Experience>> getDoctorExperiences(@PathVariable Long doctorId) {
        List<Experience> experiences = doctorService.getDoctorExperiences(doctorId);
        return ResponseEntity.ok(experiences);
    }

    @PutMapping("/experiences/{experienceId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Experience> updateExperience(@PathVariable Long experienceId, @RequestBody CreateExperienceRequest request) {
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

    @DeleteMapping("/experiences/{experienceId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteExperience(@PathVariable Long experienceId) {
        try {
            doctorService.deleteExperience(experienceId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Education Management
    @PostMapping("/{doctorId}/educations")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Education> addEducation(@PathVariable Long doctorId, @RequestBody CreateEducationRequest request) {
        try {
            Education education = new Education();
            education.setDegree(request.getDegree());
            education.setInstitution(request.getInstitution());
            education.setYearOfCompletion(request.getYearOfCompletion());
            education.setDetails(request.getDetails());
            
            Education savedEducation = doctorService.addEducation(doctorId, education);
            return ResponseEntity.ok(savedEducation);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{doctorId}/educations")
    public ResponseEntity<List<Education>> getDoctorEducations(@PathVariable Long doctorId) {
        List<Education> educations = doctorService.getDoctorEducations(doctorId);
        return ResponseEntity.ok(educations);
    }

    @PutMapping("/educations/{educationId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Education> updateEducation(@PathVariable Long educationId, @RequestBody CreateEducationRequest request) {
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

    @DeleteMapping("/educations/{educationId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteEducation(@PathVariable Long educationId) {
        try {
            doctorService.deleteEducation(educationId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Certificate Management
    @PostMapping("/{doctorId}/certificates")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Certificate> addCertificate(@PathVariable Long doctorId, @RequestBody CreateCertificateRequest request) {
        try {
            Certificate certificate = new Certificate();
            certificate.setName(request.getName());
            certificate.setUrl(request.getUrl());
            certificate.setDetails(request.getDetails());
            
            Certificate savedCertificate = doctorService.addCertificate(doctorId, certificate);
            return ResponseEntity.ok(savedCertificate);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{doctorId}/certificates")
    public ResponseEntity<List<Certificate>> getDoctorCertificates(@PathVariable Long doctorId) {
        List<Certificate> certificates = doctorService.getDoctorCertificates(doctorId);
        return ResponseEntity.ok(certificates);
    }

    @PutMapping("/certificates/{certificateId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Certificate> updateCertificate(@PathVariable Long certificateId, @RequestBody CreateCertificateRequest request) {
        try {
            Certificate certificate = new Certificate();
            certificate.setName(request.getName());
            certificate.setUrl(request.getUrl());
            certificate.setDetails(request.getDetails());
            
            Certificate updatedCertificate = doctorService.updateCertificate(certificateId, certificate);
            return ResponseEntity.ok(updatedCertificate);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/certificates/{certificateId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteCertificate(@PathVariable Long certificateId) {
        try {
            doctorService.deleteCertificate(certificateId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
} 