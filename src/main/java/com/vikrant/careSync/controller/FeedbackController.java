package com.vikrant.careSync.controller;

import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.dto.CreateFeedbackRequest;
import com.vikrant.careSync.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<?> createFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        try {
            Feedback feedback = new Feedback();
            feedback.setRating(request.getRating());
            feedback.setComment(request.getComment());
            
            Feedback createdFeedback = feedbackService.createFeedback(feedback);
            return ResponseEntity.ok(createdFeedback);
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
            return ResponseEntity.ok(feedback);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<?> getFeedbackByDoctor(@PathVariable Long doctorId) {
        try {
            List<Feedback> feedbacks = feedbackService.getFeedbackByDoctor(doctorId);
            return ResponseEntity.ok(feedbacks);
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
            return ResponseEntity.ok(feedbacks);
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
            return ResponseEntity.ok(feedback);
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
            return ResponseEntity.ok(updatedFeedback);
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