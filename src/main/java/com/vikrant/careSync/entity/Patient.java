package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"appointments", "medicalHistories"})
@EqualsAndHashCode(exclude = {"appointments", "medicalHistories"})
@Entity
@Table(name = "patients")
public class Patient extends User {
    
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "contact_info", length = 100)
    private String contactInfo;
    
    @Column(name = "illness_details", length = 1000)
    private String illnessDetails;
    
    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Appointment> appointments;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<MedicalHistory> medicalHistories;

    public String getName() {
        return firstName + " " + lastName;
    }

    // Helper method to check if patient can book appointment
    public boolean canBookAppointment() {
        return this.getIsActive() != null && this.getIsActive();
    }
} 