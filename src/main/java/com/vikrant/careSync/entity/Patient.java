package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.vikrant.careSync.security.EncryptionConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = { "appointments", "medicalHistories", "documents" })
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "patients")
public class Patient extends User {

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "first_name", nullable = false, length = 200) // Increased length for encrypted data
    private String firstName;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "last_name", nullable = false, length = 200)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "illness_details", length = 2000)
    private String illnessDetails;

    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(name = "is_active")
    private Boolean isActive;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Appointment> appointments;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<MedicalHistory> medicalHistories;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Document> documents;

    public String getName() {
        return firstName + " " + lastName;
    }

    // Helper method to check if patient can book appointment
    public boolean canBookAppointment() {
        return this.getIsActive() != null && this.getIsActive();
    }
}