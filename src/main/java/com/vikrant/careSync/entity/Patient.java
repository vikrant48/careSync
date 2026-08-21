package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.vikrant.careSync.security.EncryptionConverter;
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
@ToString(exclude = { "user", "appointments", "medicalHistories", "documents" })
@EqualsAndHashCode(exclude = { "user", "appointments", "medicalHistories", "documents" })
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

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

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Appointment> appointments;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<MedicalHistory> medicalHistories;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
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

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private User.Role role = User.Role.PATIENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (role == null) {
            role = User.Role.PATIENT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public User.Role getRole() {
        if (user != null && user.getRole() != null)
            return user.getRole();
        return role != null ? role : User.Role.PATIENT;
    }

    public void setRole(User.Role role) {
        this.role = role;
        if (user != null)
            user.setRole(role);
    }

    public Boolean getIsActive() {
        if (user != null && user.getIsActive() != null)
            return user.getIsActive();
        return isActive != null ? isActive : true;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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
        if (user != null && user.getCreatedAt() != null)
            return user.getCreatedAt();
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        if (user != null && user.getUpdatedAt() != null)
            return user.getUpdatedAt();
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        if (user != null)
            user.setUpdatedAt(updatedAt);
    }

    public String getName() {
        return firstName + " " + lastName;
    }

    // Helper method to check if patient can book appointment
    public boolean canBookAppointment() {
        return this.getIsActive() != null && this.getIsActive();
    }
}