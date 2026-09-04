package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeedUserRequest {
    private Long userId;
    private String username;
    private String password;
    private String email;
    private String role; // "PATIENT", "DOCTOR", "ADMIN" (defaults to PATIENT if omitted)
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String contactInfo;
    private String illnessDetails;
    private String gender;
    private String bloodGroup;

    // Doctor specific fields
    private String specialization;
    private BigDecimal consultationFees;
    private String address;
    private String languages;
}
