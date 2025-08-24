package com.vikrant.careSync.dto;

import jakarta.validation.constraints.Email;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDoctorRequest {
    private String firstName;
    private String lastName;
    private String specialization;
    private String contactInfo;
    private String profileImageUrl;
    private String email;
    private Boolean isActive;
}
