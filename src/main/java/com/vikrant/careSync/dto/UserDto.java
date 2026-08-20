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
        if (user != null) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.email = user.getEmail();
            this.role = user.getRole() != null ? user.getRole().name() : null;
            this.createdAt = user.getCreatedAt();
            this.updatedAt = user.getUpdatedAt();
            this.isActive = user.getIsActive();
            this.lastLogin = user.getLastLogin();
        }
    }

    public UserDto(Doctor doctor) {
        if (doctor != null) {
            User user = doctor.getUser();
            if (user != null) {
                this.id = user.getId();
                this.username = user.getUsername();
                this.email = user.getEmail();
                this.role = user.getRole() != null ? user.getRole().name() : null;
                this.createdAt = user.getCreatedAt();
                this.updatedAt = user.getUpdatedAt();
                this.isActive = user.getIsActive();
                this.lastLogin = user.getLastLogin();
            } else {
                this.id = doctor.getId();
            }
            this.firstName = doctor.getFirstName();
            this.lastName = doctor.getLastName();
            this.phoneNumber = doctor.getContactInfo();
            this.address = doctor.getAddress() != null ? doctor.getAddress() : "";
        }
    }

    public UserDto(Patient patient) {
        if (patient != null) {
            User user = patient.getUser();
            if (user != null) {
                this.id = user.getId();
                this.username = user.getUsername();
                this.email = user.getEmail();
                this.role = user.getRole() != null ? user.getRole().name() : null;
                this.createdAt = user.getCreatedAt();
                this.updatedAt = user.getUpdatedAt();
                this.isActive = user.getIsActive();
                this.lastLogin = user.getLastLogin();
            } else {
                this.id = patient.getId();
            }
            this.firstName = patient.getFirstName();
            this.lastName = patient.getLastName();
            this.phoneNumber = patient.getContactInfo();
            this.address = "";
        }
    }
}
