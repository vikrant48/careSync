package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"experiences", "educations", "certificates", "appointments", "feedbacks", "documents"})
@EqualsAndHashCode(exclude = {"experiences", "educations", "certificates", "appointments", "feedbacks", "documents"})
@Entity
@Table(name = "doctors")
public class Doctor extends User {
    
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "contact_info", length = 100)
    private String contactInfo;
    
    @Column(name = "specialization", length = 100)
    private String specialization;
    
    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "consultation_fees")
    private java.math.BigDecimal consultationFees;

    @Column(name = "address", length = 255)
    private String address;

    // Comma separated list of languages (e.g., "Hindi,Telugu,English")
    @Column(name = "languages", length = 255)
    private String languages;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Experience> experiences;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Education> educations;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Certificate> certificates;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Appointment> appointments;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Feedback> feedbacks;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Document> documents;

    public String getName() {
        return firstName + " " + lastName;
    }

    // Helper method to check if doctor can accept appointments
    public boolean canAcceptAppointments() {
        return this.getIsActive() != null && this.getIsActive();
    }
}