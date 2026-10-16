package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.entity.Feedback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final AppointmentService appointmentService;
    private final DoctorService doctorService;
    private final PatientService patientService;
    private final FeedbackService feedbackService;
    private final com.vikrant.careSync.repository.AppointmentRepository appointmentRepository;
    private final com.vikrant.careSync.repository.DoctorRepository doctorRepository;
    private final com.vikrant.careSync.repository.PatientRepository patientRepository;
    private final com.vikrant.careSync.repository.FeedbackRepository feedbackRepository;

    // Doctor Performance Reports
    public Map<String, Object> getDoctorPerformanceReport(Long doctorId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> report = new HashMap<>();

        List<Appointment> appointments = appointmentService.getAppointmentsByDateRange(
                doctorId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

        long totalAppointments = appointments.size();
        long completedAppointments = appointments.stream()
                .filter(a -> a.getStatus() == Appointment.Status.COMPLETED).count();
        long cancelledAppointments = appointments.stream()
                .filter(a -> a.getStatus() == Appointment.Status.CANCELLED).count();
        long noShowAppointments = totalAppointments - completedAppointments - cancelledAppointments;

        double completionRate = totalAppointments > 0 ? (double) completedAppointments / totalAppointments * 100 : 0;
        double cancellationRate = totalAppointments > 0 ? (double) cancelledAppointments / totalAppointments * 100 : 0;
        double noShowRate = totalAppointments > 0 ? (double) noShowAppointments / totalAppointments * 100 : 0;

        double averageRating = feedbackService.getAverageRatingByDoctor(doctorId);

        report.put("totalAppointments", totalAppointments);
        report.put("completedAppointments", completedAppointments);
        report.put("cancelledAppointments", cancelledAppointments);
        report.put("noShowAppointments", noShowAppointments);
        report.put("completionRate", Math.round(completionRate * 100.0) / 100.0);
        report.put("cancellationRate", Math.round(cancellationRate * 100.0) / 100.0);
        report.put("noShowRate", Math.round(noShowRate * 100.0) / 100.0);
        report.put("averageRating", Math.round(averageRating * 100.0) / 100.0);
        report.put("startDate", startDate);
        report.put("endDate", endDate);

        return report;
    }

    // Patient Analytics
    public Map<String, Object> getPatientAnalytics(Long patientId) {
        Map<String, Object> analytics = new HashMap<>();

        List<Appointment> appointments = appointmentService.getAppointmentsByPatient(patientId);

        long totalVisits = appointments.stream()
                .filter(a -> a.getStatus() == Appointment.Status.COMPLETED).count();

        long totalAppointments = appointments.size();
        long cancelledAppointments = appointments.stream()
                .filter(a -> a.getStatus() == Appointment.Status.CANCELLED).count();

        // Calculate visit frequency
        double averageVisitsPerMonth = calculateAverageVisitsPerMonth(appointments);

        // Get most visited doctors
        Map<Long, Long> doctorVisitCount = appointments.stream()
                .filter(a -> a.getStatus() == Appointment.Status.COMPLETED)
                .collect(Collectors.groupingBy(
                        a -> a.getDoctor().getId(),
                        Collectors.counting()));

        analytics.put("totalVisits", totalVisits);
        analytics.put("totalAppointments", totalAppointments);
        analytics.put("cancelledAppointments", cancelledAppointments);
        analytics.put("averageVisitsPerMonth", Math.round(averageVisitsPerMonth * 100.0) / 100.0);
        analytics.put("doctorVisitCount", doctorVisitCount);

        return analytics;
    }

    // Clinic Overview Report
    public Map<String, Object> getClinicOverviewReport(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> report = new HashMap<>();

        long doctorCount = doctorRepository.count();
        long patientCount = patientRepository.count();

        long totalAppointments = 0;
        long totalCompletedAppointments = 0;
        long totalCancelledAppointments = 0;
        double totalRevenue = 0; // This would be calculated based on your pricing model

        for (Object[] row : appointmentRepository.countByStatusBetween(startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay())) {
            long count = ((Number) row[1]).longValue();
            totalAppointments += count;
            if (row[0] == Appointment.Status.COMPLETED) {
                totalCompletedAppointments += count;
            } else if (row[0] == Appointment.Status.CANCELLED) {
                totalCancelledAppointments += count;
            }
        }

        // Average over all doctors of each doctor's average rating (0 for a doctor without feedback, as before)
        double ratingSum = averageRatingPerDoctor().values().stream().mapToDouble(Double::doubleValue).sum();
        double averageClinicRating = doctorCount > 0 ? ratingSum / doctorCount : 0.0;

        report.put("totalDoctors", doctorCount);
        report.put("totalPatients", patientCount);
        report.put("totalAppointments", totalAppointments);
        report.put("totalCompletedAppointments", totalCompletedAppointments);
        report.put("totalCancelledAppointments", totalCancelledAppointments);
        report.put("completionRate",
                totalAppointments > 0
                        ? Math.round((double) totalCompletedAppointments / totalAppointments * 10000.0) / 100.0
                        : 0);
        report.put("averageClinicRating", Math.round(averageClinicRating * 100.0) / 100.0);
        report.put("estimatedRevenue", totalRevenue);
        report.put("startDate", startDate);
        report.put("endDate", endDate);

        return report;
    }

    // Appointment Trends Report
    public Map<String, Object> getAppointmentTrendsReport(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> report = new HashMap<>();

        Map<String, Long> dailyAppointments = new LinkedHashMap<>();
        Map<String, Long> weeklyAppointments = new LinkedHashMap<>();
        Map<String, Long> monthlyAppointments = new LinkedHashMap<>();

        // Daily counts from one grouped query (was one query per doctor)
        for (Object[] row : appointmentRepository.countPerDayBetween(startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay())) {
            dailyAppointments.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        // Calculate weekly and monthly trends
        dailyAppointments.forEach((date, count) -> {
            LocalDate localDate = LocalDate.parse(date);
            String week = localDate.format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
            String month = localDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            weeklyAppointments.merge(week, count, Long::sum);
            monthlyAppointments.merge(month, count, Long::sum);
        });

        report.put("dailyTrends", dailyAppointments);
        report.put("weeklyTrends", weeklyAppointments);
        report.put("monthlyTrends", monthlyAppointments);
        report.put("startDate", startDate);
        report.put("endDate", endDate);

        return report;
    }

    // Specialization Analysis
    public Map<String, Object> getSpecializationAnalysis() {
        Map<String, Object> analysis = new HashMap<>();

        // Three grouped queries instead of loading every doctor and, per doctor, all appointments and feedback
        List<Object[]> doctorRows = doctorRepository.findSpecializationRows();
        Map<Long, Double> ratingByDoctor = averageRatingPerDoctor();
        Map<Long, Long> appointmentsByDoctor = new HashMap<>();
        for (Object[] row : appointmentRepository.countPerDoctor()) {
            appointmentsByDoctor.put((Long) row[0], ((Number) row[1]).longValue());
        }

        Map<String, Long> specializationCount = new HashMap<>();
        for (Object[] row : doctorRows) {
            String specialization = row[1] != null ? (String) row[1] : "Unspecified";
            specializationCount.merge(specialization, 1L, Long::sum);
        }

        Map<String, Double> specializationRatings = new HashMap<>();
        Map<String, Long> specializationAppointments = new HashMap<>();

        for (String specialization : specializationCount.keySet()) {
            // As before: active doctors whose specialization matches, ignoring case
            List<Long> specDoctorIds = doctorRows.stream()
                    .filter(row -> Boolean.TRUE.equals(row[2]) && row[1] != null
                            && ((String) row[1]).equalsIgnoreCase(specialization))
                    .map(row -> (Long) row[0])
                    .toList();

            double avgRating = specDoctorIds.stream()
                    .mapToDouble(id -> ratingByDoctor.getOrDefault(id, 0.0))
                    .average()
                    .orElse(0.0);
            long totalAppointments = specDoctorIds.stream()
                    .mapToLong(id -> appointmentsByDoctor.getOrDefault(id, 0L))
                    .sum();

            specializationRatings.put(specialization, Math.round(avgRating * 100.0) / 100.0);
            specializationAppointments.put(specialization, totalAppointments);
        }

        analysis.put("specializationCount", specializationCount);
        analysis.put("specializationRatings", specializationRatings);
        analysis.put("specializationAppointments", specializationAppointments);

        return analysis;
    }

    // Patient Demographics Report
    public Map<String, Object> getPatientDemographicsReport() {
        Map<String, Object> report = new HashMap<>();

        List<Patient> patients = patientService.getAllPatients();

        // Age distribution
        Map<String, Long> ageGroups = patients.stream()
                .collect(Collectors.groupingBy(
                        patient -> getAgeGroup(patient.getDateOfBirth()),
                        Collectors.counting()));

        // Illness distribution
        Map<String, Long> illnessDistribution = patients.stream()
                .filter(p -> p.getIllnessDetails() != null && !p.getIllnessDetails().isEmpty())
                .collect(Collectors.groupingBy(
                        Patient::getIllnessDetails,
                        Collectors.counting()));

        report.put("totalPatients", patients.size());
        report.put("ageGroups", ageGroups);
        report.put("illnessDistribution", suppressSmallGroups(illnessDistribution));

        return report;
    }

    // Groups this small can point to a single patient; they are reported together as "Other"
    static final int MIN_GROUP_SIZE = 5;

    static Map<String, Long> suppressSmallGroups(Map<String, Long> counts) {
        Map<String, Long> result = new HashMap<>();
        long other = 0;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getValue() >= MIN_GROUP_SIZE) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                other += entry.getValue();
            }
        }
        if (other > 0) {
            result.put("Other (groups under " + MIN_GROUP_SIZE + ")", other);
        }
        return result;
    }

    // Revenue Analysis (if pricing is implemented)
    public Map<String, Object> getRevenueAnalysis(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> analysis = new HashMap<>();

        // This would be implemented based on your pricing model
        // For now, returning placeholder data
        analysis.put("totalRevenue", 0.0);
        analysis.put("revenueByDoctor", new HashMap<>());
        analysis.put("revenueBySpecialization", new HashMap<>());
        analysis.put("revenueTrends", new HashMap<>());
        analysis.put("startDate", startDate);
        analysis.put("endDate", endDate);

        return analysis;
    }

    // Helper methods
    private double calculateAverageVisitsPerMonth(List<Appointment> appointments) {
        if (appointments.isEmpty())
            return 0.0;

        LocalDateTime firstAppointment = appointments.stream()
                .map(Appointment::getAppointmentDateTime)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        LocalDateTime lastAppointment = appointments.stream()
                .map(Appointment::getAppointmentDateTime)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(
                firstAppointment.toLocalDate(), lastAppointment.toLocalDate()) + 1;

        long completedVisits = appointments.stream()
                .filter(a -> a.getStatus() == Appointment.Status.COMPLETED).count();

        return monthsBetween > 0 ? (double) completedVisits / monthsBetween : 0.0;
    }

    private String getAgeGroup(LocalDate dateOfBirth) {
        return com.vikrant.careSync.util.AgeGroups.of(dateOfBirth);
    }

    private Map<Long, Double> averageRatingPerDoctor() {
        Map<Long, Double> averages = new HashMap<>();
        for (Object[] row : feedbackRepository.averageRatingPerDoctor()) {
            averages.put((Long) row[0], ((Number) row[1]).doubleValue());
        }
        return averages;
    }
}
