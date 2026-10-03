package com.vikrant.careSync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentAnalyticsResponse {

    private long totalAppointments;
    private long completedAppointments;
    private long noShowAppointments;
    private long cancelledAppointments;

    private double completionRate;
    private double noShowRate;
    private double cancellationRate;

    private Map<String, Long> statusBreakdown;
    private Map<String, Double> funnelConversion;
}
