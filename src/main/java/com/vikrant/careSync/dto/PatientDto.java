package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Patient;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDto {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String firstName;
    private String lastName;
    private String name;
    private String profileImageUrl;
    private LocalDate dateOfBirth;
    private String contactInfo;
    private String illnessDetails;
    private String gender;
    private String bloodGroup;
    private Boolean isActive;
    private int completionPercentage;

    public PatientDto(Patient patient) {
        this.id = patient.getId();
        this.username = patient.getUsername();
        this.email = patient.getEmail();
        this.role = patient.getRole() != null ? patient.getRole().name() : null;
        this.firstName = patient.getFirstName();
        this.lastName = patient.getLastName();
        this.name = patient.getName();
        this.profileImageUrl = patient.getProfileImageUrl();
        this.dateOfBirth = patient.getDateOfBirth();
        this.contactInfo = patient.getContactInfo();
        this.illnessDetails = patient.getIllnessDetails();
        this.gender = patient.getGender();
        this.bloodGroup = patient.getBloodGroup();
        this.isActive = patient.getIsActive();
    }
}
