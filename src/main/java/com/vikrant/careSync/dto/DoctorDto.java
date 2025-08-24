package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Doctor;
import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorDto {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String firstName;
    private String lastName;
    private String name;
    private String specialization;
    private String profileImageUrl;
    private String contactInfo;
    private Boolean isActive;
    private List<ExperienceDto> experiences;
    private List<EducationDto> educations;
    private List<CertificateDto> certificates;

    public DoctorDto(Doctor doctor) {
        this.id = doctor.getId();
        this.username = doctor.getUsername();
        this.email = doctor.getEmail();
        this.role = doctor.getRole() != null ? doctor.getRole().name() : null;
        this.firstName = doctor.getFirstName();
        this.lastName = doctor.getLastName();
        this.name = doctor.getName();
        this.specialization = doctor.getSpecialization();
        this.profileImageUrl = doctor.getProfileImageUrl();
        this.contactInfo = doctor.getContactInfo();
        this.isActive = doctor.getIsActive();
        
        if (doctor.getExperiences() != null) {
            this.experiences = doctor.getExperiences().stream()
                    .map(ExperienceDto::new)
                    .collect(Collectors.toList());
        }
        
        if (doctor.getEducations() != null) {
            this.educations = doctor.getEducations().stream()
                    .map(EducationDto::new)
                    .collect(Collectors.toList());
        }
        
        if (doctor.getCertificates() != null) {
            this.certificates = doctor.getCertificates().stream()
                    .map(CertificateDto::new)
                    .collect(Collectors.toList());
        }
    }
}
