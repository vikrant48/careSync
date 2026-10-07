package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Appointment;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ESignCompletionResponse {
    private Long appointmentId;
    private Appointment.Status status;
    private LocalDateTime signedAt;
    private String signedByName;
    private String contentHash;
    private String pdfUrl;
    private String message;
}
