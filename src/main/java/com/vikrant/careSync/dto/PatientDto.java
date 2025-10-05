package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Patient;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

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
    private Boolean isActive;
    private List<MedicalHistoryDto> medicalHistories;
    private List<AppointmentDto> appointments;

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
        this.isActive = patient.getIsActive();

        if (patient.getMedicalHistories() != null) {
            this.medicalHistories = patient.getMedicalHistories().stream()
                    .map(MedicalHistoryDto::new)
                    .collect(Collectors.toList());
        }
        
        if (patient.getAppointments() != null) {
            this.appointments = patient.getAppointments().stream()
                    .map(AppointmentDto::new)
                    .collect(Collectors.toList());
        }
    }
}
