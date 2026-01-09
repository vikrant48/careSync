package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.dto.OverallAnalyticsDto;
import com.vikrant.careSync.dto.PatientFinancialStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

        private final AppointmentService appointmentService;
        private final DoctorService doctorService;
        private final PatientService patientService;
        private final FeedbackService feedbackService;
        private final com.vikrant.careSync.repository.PaymentRepository paymentRepository;

        // Peak Hours Analysis
        public Map<String, Object> getPeakHoursAnalysis(Long doctorId, LocalDate startDate, LocalDate endDate) {
                Map<String, Object> analysis = new HashMap<>();

                List<Appointment> appointments = appointmentService.getAppointmentsByDateRange(
                                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

                Map<Integer, Long> hourlyDistribution = appointments.stream()
                                .collect(Collectors.groupingBy(
                                                appointment -> appointment.getAppointmentDateTime().getHour(),
                                                Collectors.counting()));

                // Find peak hours
                Map.Entry<Integer, Long> peakHour = hourlyDistribution.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .orElse(null);

                analysis.put("hourlyDistribution", hourlyDistribution);
                analysis.put("peakHour", peakHour != null ? peakHour.getKey() : 0);
                analysis.put("peakHourAppointments", peakHour != null ? peakHour.getValue() : 0);
                analysis.put("totalAppointments", appointments.size());

                return analysis;
        }

        // Day of Week Analysis
        public Map<String, Object> getDayOfWeekAnalysis(Long doctorId, LocalDate startDate, LocalDate endDate) {
                Map<String, Object> analysis = new HashMap<>();

                List<Appointment> appointments = appointmentService.getAppointmentsByDateRange(
                                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

                Map<DayOfWeek, Long> dayDistribution = appointments.stream()
                                .collect(Collectors.groupingBy(
                                                appointment -> appointment.getAppointmentDateTime().getDayOfWeek(),
                                                Collectors.counting()));

                // Find busiest day
                Map.Entry<DayOfWeek, Long> busiestDay = dayDistribution.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .orElse(null);

                analysis.put("dayDistribution", dayDistribution);
                analysis.put("busiestDay", busiestDay != null ? busiestDay.getKey().toString() : "None");
                analysis.put("busiestDayAppointments", busiestDay != null ? busiestDay.getValue() : 0);

                return analysis;
        }

        // Patient Retention Analysis
        public Map<String, Object> getPatientRetentionAnalysis(Long doctorId) {
                Map<String, Object> analysis = new HashMap<>();

                List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(doctorId);

                Map<Long, List<Appointment>> patientAppointments = appointments.stream()
                                .collect(Collectors.groupingBy(appointment -> appointment.getPatient().getId()));

                long newPatients = patientAppointments.values().stream()
                                .filter(appointmentList -> appointmentList.size() == 1)
                                .count();

                long returningPatients = patientAppointments.values().stream()
                                .filter(appointmentList -> appointmentList.size() > 1)
                                .count();

                long totalPatients = patientAppointments.size();
                double retentionRate = totalPatients > 0 ? (double) returningPatients / totalPatients * 100 : 0;

                analysis.put("totalPatients", totalPatients);
                analysis.put("newPatients", newPatients);
                analysis.put("returningPatients", returningPatients);
                analysis.put("retentionRate", Math.round(retentionRate * 100.0) / 100.0);

                return analysis;
        }

        // Appointment Duration Analysis
        public Map<String, Object> getAppointmentDurationAnalysis(Long doctorId, LocalDate startDate,
                        LocalDate endDate) {
                Map<String, Object> analysis = new HashMap<>();

                List<Appointment> appointments = appointmentService.getAppointmentsByDateRange(
                                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

                // Group appointments by date to analyze gaps
                Map<LocalDate, List<Appointment>> appointmentsByDate = appointments.stream()
                                .collect(Collectors.groupingBy(
                                                appointment -> appointment.getAppointmentDateTime().toLocalDate()));

                List<Long> gaps = new ArrayList<>();
                for (List<Appointment> dayAppointments : appointmentsByDate.values()) {
                        dayAppointments.sort(Comparator.comparing(Appointment::getAppointmentDateTime));

                        for (int i = 0; i < dayAppointments.size() - 1; i++) {
                                long gapMinutes = java.time.temporal.ChronoUnit.MINUTES.between(
                                                dayAppointments.get(i).getAppointmentDateTime(),
                                                dayAppointments.get(i + 1).getAppointmentDateTime());
                                gaps.add(gapMinutes);
                        }
                }

                double averageGap = gaps.isEmpty() ? 0 : gaps.stream().mapToLong(Long::longValue).average().orElse(0);
                long minGap = gaps.isEmpty() ? 0 : gaps.stream().mapToLong(Long::longValue).min().orElse(0);
                long maxGap = gaps.isEmpty() ? 0 : gaps.stream().mapToLong(Long::longValue).max().orElse(0);

                analysis.put("averageGapMinutes", Math.round(averageGap * 100.0) / 100.0);
                analysis.put("minGapMinutes", minGap);
                analysis.put("maxGapMinutes", maxGap);
                analysis.put("totalGaps", gaps.size());

                return analysis;
        }

        // Feedback Sentiment Analysis
        public Map<String, Object> getFeedbackSentimentAnalysis(Long doctorId) {
                Map<String, Object> analysis = new HashMap<>();

                List<Feedback> feedbacks = feedbackService.getFeedbackByDoctor(doctorId);

                Map<Integer, Long> ratingDistribution = feedbacks.stream()
                                .collect(Collectors.groupingBy(Feedback::getRating, Collectors.counting()));

                long positiveFeedbacks = feedbacks.stream()
                                .filter(feedback -> feedback.getRating() >= 4)
                                .count();

                long neutralFeedbacks = feedbacks.stream()
                                .filter(feedback -> feedback.getRating() == 3)
                                .count();

                long negativeFeedbacks = feedbacks.stream()
                                .filter(feedback -> feedback.getRating() <= 2)
                                .count();

                long totalFeedbacks = feedbacks.size();

                double positivePercentage = totalFeedbacks > 0 ? (double) positiveFeedbacks / totalFeedbacks * 100 : 0;
                double neutralPercentage = totalFeedbacks > 0 ? (double) neutralFeedbacks / totalFeedbacks * 100 : 0;
                double negativePercentage = totalFeedbacks > 0 ? (double) negativeFeedbacks / totalFeedbacks * 100 : 0;

                analysis.put("ratingDistribution", ratingDistribution);
                analysis.put("positiveFeedbacks", positiveFeedbacks);
                analysis.put("neutralFeedbacks", neutralFeedbacks);
                analysis.put("negativeFeedbacks", negativeFeedbacks);
                analysis.put("positivePercentage", Math.round(positivePercentage * 100.0) / 100.0);
                analysis.put("neutralPercentage", Math.round(neutralPercentage * 100.0) / 100.0);
                analysis.put("negativePercentage", Math.round(negativePercentage * 100.0) / 100.0);

                return analysis;
        }

        // Seasonal Trends Analysis
        public Map<String, Object> getSeasonalTrendsAnalysis(Long doctorId, int year) {
                Map<String, Object> analysis = new HashMap<>();

                LocalDate startDate = LocalDate.of(year, 1, 1);
                LocalDate endDate = LocalDate.of(year, 12, 31);

                List<Appointment> appointments = appointmentService.getAppointmentsByDateRange(
                                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

                Map<String, Long> monthlyDistribution = appointments.stream()
                                .collect(Collectors.groupingBy(
                                                appointment -> appointment.getAppointmentDateTime()
                                                                .format(DateTimeFormatter.ofPattern("MMMM")),
                                                Collectors.counting()));

                Map<String, Long> seasonalDistribution = new HashMap<>();
                seasonalDistribution.put("Spring", getSeasonalCount(appointments, 3, 5));
                seasonalDistribution.put("Summer", getSeasonalCount(appointments, 6, 8));
                seasonalDistribution.put("Fall", getSeasonalCount(appointments, 9, 11));
                seasonalDistribution.put("Winter", getSeasonalCount(appointments, 12, 2));

                analysis.put("monthlyDistribution", monthlyDistribution);
                analysis.put("seasonalDistribution", seasonalDistribution);
                analysis.put("year", year);

                return analysis;
        }

        // Patient Demographics Analysis
        public Map<String, Object> getPatientDemographicsAnalysis(Long doctorId) {
                Map<String, Object> analysis = new HashMap<>();

                List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(doctorId);

                // Age distribution
                Map<String, Long> ageGroups = appointments.stream()
                                .map(appointment -> appointment.getPatient())
                                .distinct()
                                .collect(Collectors.groupingBy(
                                                patient -> getAgeGroup(patient.getDateOfBirth()),
                                                Collectors.counting()));

                // Gender distribution (if implemented)
                Map<String, Long> genderDistribution = new HashMap<>();
                // This would be implemented when gender field is added to Patient entity

                analysis.put("ageGroups", ageGroups);
                analysis.put("genderDistribution", genderDistribution);
                analysis.put("totalUniquePatients", appointments.stream()
                                .map(appointment -> appointment.getPatient().getId())
                                .distinct()
                                .count());

                return analysis;
        }

        // Cancellation Pattern Analysis
        public Map<String, Object> getCancellationPatternAnalysis(Long doctorId, LocalDate startDate,
                        LocalDate endDate) {
                Map<String, Object> analysis = new HashMap<>();

                List<Appointment> appointments = appointmentService.getAppointmentsByDateRange(
                                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

                List<Appointment> cancelledAppointments = appointments.stream()
                                .filter(appointment -> appointment.getStatus() == Appointment.Status.CANCELLED)
                                .collect(Collectors.toList());

                // Analyze cancellation timing
                Map<String, Long> cancellationTiming = cancelledAppointments.stream()
                                .collect(Collectors.groupingBy(
                                                appointment -> getCancellationTiming(
                                                                appointment.getAppointmentDateTime()),
                                                Collectors.counting()));

                // Day of week cancellation pattern
                Map<DayOfWeek, Long> dayOfWeekCancellations = cancelledAppointments.stream()
                                .collect(Collectors.groupingBy(
                                                appointment -> appointment.getAppointmentDateTime().getDayOfWeek(),
                                                Collectors.counting()));

                analysis.put("totalCancellations", cancelledAppointments.size());
                analysis.put("cancellationRate",
                                appointments.size() > 0
                                                ? (double) cancelledAppointments.size() / appointments.size() * 100
                                                : 0);
                analysis.put("cancellationTiming", cancellationTiming);
                analysis.put("dayOfWeekCancellations", dayOfWeekCancellations);

                return analysis;
        }

        // Helper methods
        private long getSeasonalCount(List<Appointment> appointments, int startMonth, int endMonth) {
                return appointments.stream()
                                .filter(appointment -> {
                                        int month = appointment.getAppointmentDateTime().getMonthValue();
                                        if (startMonth <= endMonth) {
                                                return month >= startMonth && month <= endMonth;
                                        } else {
                                                // For winter (Dec-Feb)
                                                return month >= startMonth || month <= endMonth;
                                        }
                                })
                                .count();
        }

        private String getAgeGroup(LocalDate dateOfBirth) {
                long age = java.time.temporal.ChronoUnit.YEARS.between(dateOfBirth, LocalDate.now());

                if (age < 18)
                        return "Under 18";
                else if (age < 30)
                        return "18-29";
                else if (age < 45)
                        return "30-44";
                else if (age < 60)
                        return "45-59";
                else if (age < 75)
                        return "60-74";
                else
                        return "75+";
        }

        private String getCancellationTiming(LocalDateTime appointmentTime) {
                long hoursUntilAppointment = java.time.temporal.ChronoUnit.HOURS.between(
                                LocalDateTime.now(), appointmentTime);

                if (hoursUntilAppointment < 24)
                        return "Same Day";
                else if (hoursUntilAppointment < 48)
                        return "1 Day Before";
                else if (hoursUntilAppointment < 168)
                        return "1 Week Before";
                else
                        return "More than 1 Week Before";
        }

        // Overall Analytics (System-wide)
        @Cacheable(value = "analytics", key = "'overall_' + #startDate + '_' + #endDate")
        public OverallAnalyticsDto getOverallAnalytics(LocalDate startDate, LocalDate endDate) {
                try {
                        // Get all appointments in date range
                        List<Appointment> allAppointments = appointmentService.getAllAppointmentsByDateRange(
                                        startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

                        // Get all doctors and patients
                        List<Doctor> allDoctors = doctorService.getAllDoctors();
                        List<Patient> allPatients = patientService.getAllPatients();

                        // Active users (users with appointments in date range)
                        long activeDoctors = allAppointments.stream()
                                        .map(appointment -> appointment.getDoctor().getId())
                                        .distinct()
                                        .count();

                        long activePatients = allAppointments.stream()
                                        .map(appointment -> appointment.getPatient().getId())
                                        .distinct()
                                        .count();

                        // Revenue calculation (assuming a base fee per appointment)
                        double baseAppointmentFee = 100.0; // Default fee
                        double totalRevenue = allAppointments.size() * baseAppointmentFee;

                        // Average rating from feedback
                        double avgRating = 4.5;
                        try {
                                List<Feedback> allFeedback = feedbackService.getAllFeedback();
                                avgRating = allFeedback.stream()
                                                .mapToDouble(Feedback::getRating)
                                                .average()
                                                .orElse(0.0);
                                avgRating = Math.round(avgRating * 100.0) / 100.0;
                        } catch (Exception e) {
                                // Keep default
                        }

                        // Appointment status distribution
                        Map<String, Long> statusDistribution = allAppointments.stream()
                                        .collect(Collectors.groupingBy(
                                                        appointment -> appointment.getStatus().toString(),
                                                        Collectors.counting()));

                        // Daily appointment trends
                        Map<String, Long> dailyTrends = allAppointments.stream()
                                        .collect(Collectors.groupingBy(
                                                        appointment -> appointment.getAppointmentDateTime()
                                                                        .toLocalDate().toString(),
                                                        Collectors.counting()));

                        return OverallAnalyticsDto.builder()
                                        .totalAppointments(allAppointments.size())
                                        .totalDoctors(allDoctors.size())
                                        .totalPatients(allPatients.size())
                                        .activeDoctors(activeDoctors)
                                        .activePatients(activePatients)
                                        .totalUsers(allDoctors.size() + allPatients.size())
                                        .activeUsers(activeDoctors + activePatients)
                                        .totalRevenue(totalRevenue)
                                        .avgRating(avgRating)
                                        .appointmentStatusDistribution(statusDistribution)
                                        .dailyAppointmentTrends(dailyTrends)
                                        .build();

                } catch (Exception e) {
                        // Return empty/default DTO if there's an error
                        return OverallAnalyticsDto.builder()
                                        .avgRating(4.5)
                                        .build();
                }
        }

        // Patient Financial Stats
        @Cacheable(value = "patientFinancialAnalytics_v3", key = "#patientId")
        public PatientFinancialStatsDto getPatientFinancialStats(Long patientId) {
                log.info("Fetching financial stats for patient ID: {}", patientId);
                // Get aggregated stats directly from DB
                List<Object[]> paymentStats;
                try {
                        paymentStats = paymentRepository.getPatientPaymentStats(patientId);
                        log.info("Successfully fetched {} rows from repository", paymentStats.size());
                } catch (Exception e) {
                        log.error("Database error while fetching payment stats: {}", e.getMessage(), e);
                        throw e;
                }

                java.math.BigDecimal totalAppointmentSpend = java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalLabTestSpend = java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalSpend = java.math.BigDecimal.ZERO;

                for (Object[] row : paymentStats) {
                        if (row == null || row.length < 2) {
                                log.warn("Skipping invalid row in paymentStats");
                                continue;
                        }

                        Object typeObj = row[0];
                        Object amountObj = row[1];

                        if (typeObj == null || amountObj == null) {
                                log.warn("Skipping row with null values: type={}, amount={}", typeObj, amountObj);
                                continue;
                        }

                        com.vikrant.careSync.entity.Payment.PaymentType type = null;
                        if (typeObj instanceof com.vikrant.careSync.entity.Payment.PaymentType) {
                                type = (com.vikrant.careSync.entity.Payment.PaymentType) typeObj;
                        } else {
                                try {
                                        type = com.vikrant.careSync.entity.Payment.PaymentType
                                                        .valueOf(typeObj.toString().trim().toUpperCase());
                                } catch (Exception e) {
                                        log.error("Failed to parse payment type: '{}'. Valid types: {}", typeObj,
                                                        java.util.Arrays.toString(
                                                                        com.vikrant.careSync.entity.Payment.PaymentType
                                                                                        .values()));
                                        continue;
                                }
                        }

                        java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
                        try {
                                if (amountObj instanceof java.math.BigDecimal) {
                                        amount = (java.math.BigDecimal) amountObj;
                                } else if (amountObj instanceof Number) {
                                        amount = java.math.BigDecimal.valueOf(((Number) amountObj).doubleValue());
                                } else {
                                        amount = new java.math.BigDecimal(amountObj.toString().trim());
                                }
                        } catch (Exception e) {
                                log.error("Failed to parse amount: {}", amountObj);
                                continue;
                        }

                        totalSpend = totalSpend.add(amount);

                        if (type == com.vikrant.careSync.entity.Payment.PaymentType.APPOINTMENT) {
                                totalAppointmentSpend = totalAppointmentSpend.add(amount);
                        } else if (type == com.vikrant.careSync.entity.Payment.PaymentType.LAB_TEST) {
                                totalLabTestSpend = totalLabTestSpend.add(amount);
                        }
                }

                log.info("Calculated stats for patient {}: total={}, appt={}, lab={}",
                                patientId, totalSpend, totalAppointmentSpend, totalLabTestSpend);

                return PatientFinancialStatsDto.builder()
                                .totalAppointmentSpend(totalAppointmentSpend.doubleValue())
                                .totalLabTestSpend(totalLabTestSpend.doubleValue())
                                .totalSpend(totalSpend.doubleValue())
                                .totalBookings(0L) // Default values for now
                                .confirmedBookings(0L)
                                .averageSpentPerBooking(0.0)
                                .build();
        }
}