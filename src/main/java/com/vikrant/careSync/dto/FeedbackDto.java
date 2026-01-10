package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Feedback;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackDto {
    private Long id;
    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private String patientName;
    private String doctorName;
    private int rating;
    private String comment;
    private Boolean anonymous;
    private LocalDateTime createdAt;

    public FeedbackDto(Feedback feedback) {
        this.id = feedback.getId();
        if (feedback.getAppointment() != null) {
            this.appointmentId = feedback.getAppointment().getId();
        }
        if (feedback.getPatient() != null) {
            this.patientId = feedback.getPatient().getId();
            this.patientName = feedback.getPatient().getName();
        }
        if (feedback.getDoctor() != null) {
            this.doctorId = feedback.getDoctor().getId();
            this.doctorName = feedback.getDoctor().getName();
        }
        this.rating = feedback.getRating();
        this.comment = feedback.getComment();
        this.anonymous = feedback.getAnonymous();
        this.createdAt = feedback.getCreatedAt();
    }
}
