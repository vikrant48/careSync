package com.vikrant.careSync.dto;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverallAnalyticsDto implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private long totalAppointments;
    private long totalDoctors;
    private long totalPatients;
    private long activeDoctors;
    private long activePatients;
    private long totalUsers;
    private long activeUsers;
    private double totalRevenue;
    private double avgRating;
    private Map<String, Long> appointmentStatusDistribution;
    private Map<String, Long> dailyAppointmentTrends;
}
