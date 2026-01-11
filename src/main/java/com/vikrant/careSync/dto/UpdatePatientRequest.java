package com.vikrant.careSync.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePatientRequest {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String contactInfo;
    private String illnessDetails;
    private String email;
    private Boolean isActive;
    private String gender;
    private String bloodGroup;
}
