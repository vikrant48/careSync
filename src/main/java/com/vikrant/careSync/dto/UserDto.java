package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String address;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;
    private LocalDateTime lastLogin;

    public UserDto(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.role = user.getRole() != null ? user.getRole().name() : null;
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        this.isActive = user.getIsActive();
        this.lastLogin = user.getLastLogin();
        
        // Set user-specific fields based on user type
        if (user instanceof com.vikrant.careSync.entity.Doctor) {
            com.vikrant.careSync.entity.Doctor doctor = (com.vikrant.careSync.entity.Doctor) user;
            this.firstName = doctor.getFirstName();
            this.lastName = doctor.getLastName();
            this.phoneNumber = doctor.getContactInfo();
            this.address = ""; // Doctor doesn't have address field
        } else if (user instanceof com.vikrant.careSync.entity.Patient) {
            com.vikrant.careSync.entity.Patient patient = (com.vikrant.careSync.entity.Patient) user;
            this.firstName = patient.getFirstName();
            this.lastName = patient.getLastName();
            this.phoneNumber = patient.getContactInfo();
            this.address = ""; // Patient doesn't have address field
        }
    }
}
