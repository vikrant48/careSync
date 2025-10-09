package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Experience;
import com.vikrant.careSync.entity.Education;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.dto.UpdateDoctorRequest;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.ExperienceRepository;
import com.vikrant.careSync.repository.EducationRepository;
import com.vikrant.careSync.repository.CertificateRepository;
import com.vikrant.careSync.service.interfaces.IDoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DoctorService implements IDoctorService {

    private final DoctorRepository doctorRepository;
    private final ExperienceRepository experienceRepository;
    private final EducationRepository educationRepository;
    private final CertificateRepository certificateRepository;

    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    public Optional<Doctor> getDoctorById(Long id) {
        return doctorRepository.findById(id);
    }

    public Optional<Doctor> getDoctorByUsername(String username) {
        return doctorRepository.findByUsername(username);
    }

    public Doctor updateDoctorProfile(Long doctorId, Doctor updatedDoctor) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setFirstName(updatedDoctor.getFirstName());
        doctor.setLastName(updatedDoctor.getLastName());
        doctor.setSpecialization(updatedDoctor.getSpecialization());
        doctor.setProfileImageUrl(updatedDoctor.getProfileImageUrl());

        return doctorRepository.save(doctor);
    }

    public Doctor updateDoctorProfileByUsername(String username, UpdateDoctorRequest request) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (request.getFirstName() != null) doctor.setFirstName(request.getFirstName());
        if (request.getLastName() != null) doctor.setLastName(request.getLastName());
        if (request.getSpecialization() != null) doctor.setSpecialization(request.getSpecialization());
        if (request.getContactInfo() != null) doctor.setContactInfo(request.getContactInfo());
        if (request.getEmail() != null) doctor.setEmail(request.getEmail());
        if (request.getIsActive() != null) doctor.setIsActive(request.getIsActive());
        if (request.getGender() != null) doctor.setGender(request.getGender());
        if (request.getConsultationFees() != null) doctor.setConsultationFees(java.math.BigDecimal.valueOf(request.getConsultationFees()));
        if (request.getAddress() != null) doctor.setAddress(request.getAddress());
        if (request.getLanguages() != null) doctor.setLanguages(String.join(",", request.getLanguages()));

        doctor.setUpdatedAt(LocalDateTime.now());

        return doctorRepository.save(doctor);
    }

    public Doctor updateProfileImage(Long doctorId, String imageUrl) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setProfileImageUrl(imageUrl);
        return doctorRepository.save(doctor);
    }

    public Doctor updateProfileImageByUsername(String username, String imageUrl) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setProfileImageUrl(imageUrl);
        doctor.setUpdatedAt(LocalDateTime.now());
        return doctorRepository.save(doctor);
    }

    // Experience Management
    public Experience addExperience(Long doctorId, Experience experience) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        experience.setDoctor(doctor);
        return experienceRepository.save(experience);
    }

    public Experience addExperienceByUsername(String username, Experience experience) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        experience.setDoctor(doctor);
        return experienceRepository.save(experience);
    }

    public List<Experience> getDoctorExperiences(Long doctorId) {
        return experienceRepository.findByDoctorId(doctorId);
    }

    public List<Experience> getDoctorExperiencesByUsername(String username) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        return experienceRepository.findByDoctorId(doctor.getId());
    }

    public Experience updateExperience(Long experienceId, Experience updatedExperience) {
        Experience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new RuntimeException("Experience not found"));

        experience.setHospitalName(updatedExperience.getHospitalName());
        experience.setPosition(updatedExperience.getPosition());
        experience.setYearsOfService(updatedExperience.getYearsOfService());
        experience.setDetails(updatedExperience.getDetails());

        return experienceRepository.save(experience);
    }

    public void deleteExperience(Long experienceId) {
        experienceRepository.deleteById(experienceId);
    }

    // Education Management
    public Education addEducation(Long doctorId, Education education) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        education.setDoctor(doctor);
        return educationRepository.save(education);
    }

    public Education addEducationByUsername(String username, Education education) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        education.setDoctor(doctor);
        return educationRepository.save(education);
    }

    public List<Education> getDoctorEducations(Long doctorId) {
        return educationRepository.findByDoctorId(doctorId);
    }

    public List<Education> getDoctorEducationsByUsername(String username) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        return educationRepository.findByDoctorId(doctor.getId());
    }

    public Education updateEducation(Long educationId, Education updatedEducation) {
        Education education = educationRepository.findById(educationId)
                .orElseThrow(() -> new RuntimeException("Education not found"));

        education.setDegree(updatedEducation.getDegree());
        education.setInstitution(updatedEducation.getInstitution());
        education.setYearOfCompletion(updatedEducation.getYearOfCompletion());
        education.setDetails(updatedEducation.getDetails());

        return educationRepository.save(education);
    }

    public void deleteEducation(Long educationId) {
        educationRepository.deleteById(educationId);
    }

    // Certificate Management
    public Certificate addCertificate(Long doctorId, Certificate certificate) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        certificate.setDoctor(doctor);
        return certificateRepository.save(certificate);
    }

    public Certificate addCertificateByUsername(String username, Certificate certificate) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        certificate.setDoctor(doctor);
        return certificateRepository.save(certificate);
    }

    public List<Certificate> getDoctorCertificates(Long doctorId) {
        return certificateRepository.findByDoctorId(doctorId);
    }

    public List<Certificate> getDoctorCertificatesByUsername(String username) {
        Doctor doctor = doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        return certificateRepository.findByDoctorId(doctor.getId());
    }

    public Certificate updateCertificate(Long certificateId, Certificate updatedCertificate) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));

        certificate.setName(updatedCertificate.getName());
        certificate.setUrl(updatedCertificate.getUrl());
        certificate.setDetails(updatedCertificate.getDetails());

        return certificateRepository.save(certificate);
    }

    public Certificate updateCertificateUrl(Long certificateId, String url) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        
        certificate.setUrl(url);
        return certificateRepository.save(certificate);
    }

    public void deleteCertificate(Long certificateId) {
        certificateRepository.deleteById(certificateId);
    }

    public List<Doctor> getDoctorsBySpecialization(String specialization) {
        return doctorRepository.findAll().stream()
                .filter(doctor -> specialization.equalsIgnoreCase(doctor.getSpecialization()))
                .toList();
    }

    public Doctor getDoctorProfile(String username) {
        return doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }
}