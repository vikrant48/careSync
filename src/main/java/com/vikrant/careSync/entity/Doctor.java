package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = { "user", "experiences", "educations", "certificates", "appointments", "feedbacks", "documents" })
@EqualsAndHashCode(exclude = { "user", "experiences", "educations", "certificates", "appointments", "feedbacks",
        "documents" })
@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

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

    @Column(name = "is_verified", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isVerified = false;

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

    // Helper delegate methods for User attributes
    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }

    public void setUsername(String username) {
        if (user != null)
            user.setUsername(username);
    }

    public String getPassword() {
        return user != null ? user.getPassword() : null;
    }

    public void setPassword(String password) {
        if (user != null)
            user.setPassword(password);
    }

    public String getEmail() {
        return user != null ? user.getEmail() : null;
    }

    public void setEmail(String email) {
        if (user != null)
            user.setEmail(email);
    }

    public User.Role getRole() {
        return user != null ? user.getRole() : User.Role.DOCTOR;
    }

    public void setRole(User.Role role) {
        if (user != null)
            user.setRole(role);
    }

    public Boolean getIsActive() {
        return user != null ? user.getIsActive() : true;
    }

    public void setIsActive(Boolean isActive) {
        if (user != null)
            user.setIsActive(isActive);
    }

    public LocalDateTime getLastLogin() {
        return user != null ? user.getLastLogin() : null;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        if (user != null)
            user.setLastLogin(lastLogin);
    }

    public LocalDateTime getCreatedAt() {
        return user != null ? user.getCreatedAt() : null;
    }

    public LocalDateTime getUpdatedAt() {
        return user != null ? user.getUpdatedAt() : null;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        if (user != null)
            user.setUpdatedAt(updatedAt);
    }

    public String getName() {
        return firstName + " " + lastName;
    }

    // Helper method to check if doctor can accept appointments
    public boolean canAcceptAppointments() {
        return this.getIsActive() != null && this.getIsActive();
    }
}