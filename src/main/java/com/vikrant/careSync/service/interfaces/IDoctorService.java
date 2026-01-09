package com.vikrant.careSync.service.interfaces;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Experience;
import com.vikrant.careSync.entity.Education;
import com.vikrant.careSync.entity.Certificate;
import com.vikrant.careSync.dto.*;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Doctor operations
 * Defines all business logic operations related to doctors
 */
public interface IDoctorService {

    /**
     * Retrieve all doctors
     * 
     * @return List of all doctors
     */
    List<Doctor> getAllDoctors();

    /**
     * Get doctor by ID
     * 
     * @param id Doctor ID
     * @return Optional containing doctor if found
     */
    Optional<Doctor> getDoctorById(Long id);

    Optional<Doctor> getDoctorByUsername(String username);

    List<DoctorDto> getAllDoctorsDto();

    Optional<DoctorDto> getDoctorDtoById(Long id);

    Optional<DoctorDto> getDoctorDtoByUsername(String username);

    /**
     * Update doctor profile
     * 
     * @param doctorId      Doctor ID
     * @param updatedDoctor Updated doctor information
     * @return Updated doctor
     */
    Doctor updateDoctorProfile(Long doctorId, Doctor updatedDoctor);

    /**
     * Update doctor profile by username
     * 
     * @param username Doctor username
     * @param request  Update request
     * @return Updated doctor
     */
    Doctor updateDoctorProfileByUsername(String username, UpdateDoctorRequest request);

    /**
     * Update doctor profile image
     * 
     * @param doctorId Doctor ID
     * @param imageUrl New image URL
     * @return Updated doctor
     */
    Doctor updateProfileImage(Long doctorId, String imageUrl);

    /**
     * Update doctor profile image by username
     * 
     * @param username Doctor username
     * @param imageUrl New image URL
     * @return Updated doctor
     */
    Doctor updateProfileImageByUsername(String username, String imageUrl);

    /**
     * Add experience to doctor
     * 
     * @param doctorId   Doctor ID
     * @param experience Experience to add
     * @return Added experience
     */
    Experience addExperience(Long doctorId, Experience experience);

    /**
     * Add experience to doctor by username
     * 
     * @param username   Doctor username
     * @param experience Experience to add
     * @return Added experience
     */
    Experience addExperienceByUsername(String username, Experience experience);

    List<Experience> getDoctorExperiences(Long doctorId);

    List<ExperienceDto> getDoctorExperiencesDto(Long doctorId);

    /**
     * Get doctor experiences by username
     * 
     * @param username Doctor username
     * @return List of experiences
     */
    List<Experience> getDoctorExperiencesByUsername(String username);

    /**
     * Update experience
     * 
     * @param experienceId      Experience ID
     * @param updatedExperience Updated experience
     * @return Updated experience
     */
    Experience updateExperience(Long experienceId, Experience updatedExperience);

    /**
     * Delete experience
     * 
     * @param experienceId Experience ID
     */
    void deleteExperience(Long experienceId);

    /**
     * Add education to doctor
     * 
     * @param doctorId  Doctor ID
     * @param education Education to add
     * @return Added education
     */
    Education addEducation(Long doctorId, Education education);

    /**
     * Add education to doctor by username
     * 
     * @param username  Doctor username
     * @param education Education to add
     * @return Added education
     */
    Education addEducationByUsername(String username, Education education);

    List<Education> getDoctorEducations(Long doctorId);

    List<EducationDto> getDoctorEducationsDto(Long doctorId);

    /**
     * Get doctor educations by username
     * 
     * @param username Doctor username
     * @return List of educations
     */
    List<Education> getDoctorEducationsByUsername(String username);

    /**
     * Update education
     * 
     * @param educationId      Education ID
     * @param updatedEducation Updated education
     * @return Updated education
     */
    Education updateEducation(Long educationId, Education updatedEducation);

    /**
     * Delete education
     * 
     * @param educationId Education ID
     */
    void deleteEducation(Long educationId);

    /**
     * Add certificate to doctor
     * 
     * @param doctorId    Doctor ID
     * @param certificate Certificate to add
     * @return Added certificate
     */
    Certificate addCertificate(Long doctorId, Certificate certificate);

    /**
     * Add certificate to doctor by username
     * 
     * @param username    Doctor username
     * @param certificate Certificate to add
     * @return Added certificate
     */
    Certificate addCertificateByUsername(String username, Certificate certificate);

    List<Certificate> getDoctorCertificates(Long doctorId);

    List<CertificateDto> getDoctorCertificatesDto(Long doctorId);

    /**
     * Get doctor certificates by username
     * 
     * @param username Doctor username
     * @return List of certificates
     */
    List<Certificate> getDoctorCertificatesByUsername(String username);

    /**
     * Update certificate
     * 
     * @param certificateId      Certificate ID
     * @param updatedCertificate Updated certificate
     * @return Updated certificate
     */
    Certificate updateCertificate(Long certificateId, Certificate updatedCertificate);

    /**
     * Update certificate URL
     * 
     * @param certificateId Certificate ID
     * @param url           New URL for the certificate
     * @return Updated certificate
     */
    Certificate updateCertificateUrl(Long certificateId, String url);

    /**
     * Delete certificate
     * 
     * @param certificateId Certificate ID
     */
    void deleteCertificate(Long certificateId);

    /**
     * Get doctors by specialization
     * 
     * @param specialization Specialization
     * @return List of doctors
     */
    List<Doctor> getDoctorsBySpecialization(String specialization);

    /**
     * Get doctor profile
     * 
     * @param username Doctor username
     * @return Doctor profile
     */
    Doctor getDoctorProfile(String username);
}