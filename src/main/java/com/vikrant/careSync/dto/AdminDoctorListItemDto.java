package com.vikrant.careSync.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDoctorListItemDto {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String name;
    private String specialization;
    private Boolean isVerified;
    private Boolean isActive;

    public AdminDoctorListItemDto(Long id, String username, String firstName, String lastName, String specialization,
            Boolean isVerified, Boolean isActive) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.name = (firstName != null ? firstName : "")
                + (lastName != null && !lastName.isBlank() ? " " + lastName : "");
        this.specialization = specialization;
        this.isVerified = isVerified != null && isVerified;
        this.isActive = isActive != null && isActive;
    }
}
