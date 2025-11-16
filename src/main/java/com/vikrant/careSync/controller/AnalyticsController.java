package com.vikrant.careSync.controller;

import com.vikrant.careSync.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overall")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getOverallAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            Map<String, Object> analysis = analyticsService.getOverallAnalytics(startDate, endDate);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/peak-hours")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getPeakHoursAnalysis(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            Map<String, Object> analysis = analyticsService.getPeakHoursAnalysis(doctorId, startDate, endDate);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/day-of-week")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getDayOfWeekAnalysis(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            Map<String, Object> analysis = analyticsService.getDayOfWeekAnalysis(doctorId, startDate, endDate);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/patient-retention")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getPatientRetentionAnalysis(@PathVariable Long doctorId) {
        try {
            Map<String, Object> analysis = analyticsService.getPatientRetentionAnalysis(doctorId);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/appointment-duration")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getAppointmentDurationAnalysis(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            Map<String, Object> analysis = analyticsService.getAppointmentDurationAnalysis(doctorId, startDate, endDate);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/feedback-sentiment")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getFeedbackSentimentAnalysis(@PathVariable Long doctorId) {
        try {
            Map<String, Object> analysis = analyticsService.getFeedbackSentimentAnalysis(doctorId);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/seasonal-trends")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getSeasonalTrendsAnalysis(
            @PathVariable Long doctorId,
            @RequestParam int year) {
        try {
            Map<String, Object> analysis = analyticsService.getSeasonalTrendsAnalysis(doctorId, year);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/patient-demographics")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getPatientDemographicsAnalysis(@PathVariable Long doctorId) {
        try {
            Map<String, Object> analysis = analyticsService.getPatientDemographicsAnalysis(doctorId);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/doctor/{doctorId}/cancellation-patterns")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, Object>> getCancellationPatternAnalysis(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            Map<String, Object> analysis = analyticsService.getCancellationPatternAnalysis(doctorId, startDate, endDate);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}