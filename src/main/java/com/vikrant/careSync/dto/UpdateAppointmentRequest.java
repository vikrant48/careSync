package com.vikrant.careSync.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAppointmentRequest {
    private LocalDateTime appointmentDateTime;
    private String status;
    private String reason;
}
