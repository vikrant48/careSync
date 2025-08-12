package com.vikrant.careSync.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentStatisticsDto {
    private Long totalAppointments;
    private Long completedAppointments;
    private Long cancelledAppointments;
    private Long pendingAppointments;
    private Double completionRate;
    private Double cancellationRate;
    private Long totalPatients;
    private Long totalDoctors;
}
