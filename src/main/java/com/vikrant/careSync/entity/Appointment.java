package com.vikrant.careSync.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"doctor", "patient", "feedback"})
@EqualsAndHashCode(exclude = {"doctor", "patient", "feedback"})
@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @JsonBackReference
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @JsonBackReference
    private Patient patient;

    @Column(name = "appointment_date_time", nullable = false)
    private LocalDateTime appointmentDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_changed_by")
    private String statusChangedBy;

    @OneToOne(mappedBy = "appointment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private Feedback feedback;

    public enum Status {
        BOOKED, CONFIRMED, COMPLETED, CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = Status.BOOKED;
        }
        statusChangedAt = LocalDateTime.now();
        statusChangedBy = "SYSTEM";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper method to check if status can be changed
    public boolean canChangeStatus(Status newStatus) {
        if (status == Status.CANCELLED) {
            return false; // Cannot change cancelled appointments
        }
        
        if (status == Status.COMPLETED && newStatus != Status.COMPLETED) {
            return false; // Cannot change completed appointments
        }
        
        return true;
    }

    // Helper method to change status with validation
    public void changeStatus(Status newStatus, String changedBy) {
        if (!canChangeStatus(newStatus)) {
            throw new IllegalStateException("Cannot change status from " + status + " to " + newStatus);
        }
        
        this.status = newStatus;
        this.statusChangedAt = LocalDateTime.now();
        this.statusChangedBy = changedBy;
    }
} 