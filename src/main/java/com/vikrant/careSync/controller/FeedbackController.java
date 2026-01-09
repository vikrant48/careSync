package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.dto.CreateFeedbackRequest;
import com.vikrant.careSync.dto.PatientAppointmentResponse;
import com.vikrant.careSync.dto.FeedbackDto;
import com.vikrant.careSync.service.interfaces.IFeedbackService;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class FeedbackController {

    private final IFeedbackService feedbackService;
    private final PatientRepository patientRepository;

    // Helper to get current authenticated patient as a User
    private User getCurrentPatient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return patientRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Patient not found with username: " + username));
        }
        throw new RuntimeException("User not authenticated");
    }

    @PostMapping
    public ResponseEntity<?> createFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        try {
            Feedback createdFeedback = feedbackService.submitFeedback(
                    request.getAppointmentId(),
                    request.getDoctorId(),
                    request.getRating(),
                    request.getComment(),
                    request.getAnonymous());
            return ResponseEntity.ok(new FeedbackDto(createdFeedback));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFeedbackById(@PathVariable Long id) {
        try {
            Feedback feedback = feedbackService.getFeedbackById(id);
            return ResponseEntity.ok(new FeedbackDto(feedback));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Get completed appointments for current patient that still need feedback
    @GetMapping("/patient/pending")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getPendingFeedbackForCurrentPatient() {
        try {
            User current = getCurrentPatient();
            List<PatientAppointmentResponse> pending = feedbackService
                    .getPendingFeedbackAppointmentsForPatient(current.getId());
            return ResponseEntity.ok(pending);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<?> getFeedbackByDoctor(@PathVariable Long doctorId) {
        try {
            List<FeedbackDto> dtos = feedbackService.getFeedbackByDoctor(doctorId).stream()
                    .map(FeedbackDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getFeedbackByPatient(@PathVariable Long patientId) {
        try {
            List<Feedback> feedbacks = feedbackService.getFeedbackByPatient(patientId);
            List<FeedbackDto> dtos = feedbacks.stream()
                    .map(FeedbackDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<?> getFeedbackByAppointment(@PathVariable Long appointmentId) {
        try {
            Feedback feedback = feedbackService.getFeedbackByAppointment(appointmentId);
            return ResponseEntity.ok(new FeedbackDto(feedback));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFeedback(@PathVariable Long id, @Valid @RequestBody CreateFeedbackRequest request) {
        try {
            Feedback feedback = new Feedback();
            feedback.setRating(request.getRating());
            feedback.setComment(request.getComment());

            Feedback updatedFeedback = feedbackService.updateFeedback(id, feedback);
            return ResponseEntity.ok(new FeedbackDto(updatedFeedback));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFeedback(@PathVariable Long id) {
        try {
            feedbackService.deleteFeedback(id);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Feedback deleted successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/{doctorId}/average-rating")
    public ResponseEntity<?> getAverageRatingByDoctor(@PathVariable Long doctorId) {
        try {
            double averageRating = feedbackService.getAverageRatingByDoctor(doctorId);
            Map<String, Object> response = new HashMap<>();
            response.put("doctorId", doctorId);
            response.put("averageRating", averageRating);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/{doctorId}/rating-distribution")
    public ResponseEntity<?> getRatingDistributionByDoctor(@PathVariable Long doctorId) {
        try {
            Map<Integer, Long> ratingDistribution = feedbackService.getRatingDistributionByDoctor(doctorId);
            return ResponseEntity.ok(ratingDistribution);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}