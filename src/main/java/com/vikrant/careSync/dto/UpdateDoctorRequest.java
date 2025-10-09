package com.vikrant.careSync.dto;

import jakarta.validation.constraints.Email;
import lombok.*;
import java.util.*;

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
    private String gender;
    private Double consultationFees;
    private String address;
    private List<String> languages;
}
