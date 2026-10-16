package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.dto.CreateFeedbackRequest;
import com.vikrant.careSync.dto.AppointmentResponse;
import com.vikrant.careSync.dto.FeedbackDto;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.service.interfaces.IFeedbackService;
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
    private final CurrentUserService currentUserService;


    @PostMapping
    @PreAuthorize("hasRole('PATIENT') and @access.isAppointmentPatient(#request.appointmentId)")
    public ResponseEntity<?> createFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        try {
            Feedback createdFeedback = feedbackService.submitFeedback(
                    request.getAppointmentId(),
                    request.getDoctorId(),
                    request.getRating(),
                    request.getComment(),
                    request.getAnonymous());
            return ResponseEntity.ok(new FeedbackDto(createdFeedback));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(ClientErrorMessages.errorBody(e));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFeedbackById(@PathVariable Long id) {
        try {
            Feedback feedback = feedbackService.getFeedbackById(id);
            return ResponseEntity.ok(forCurrentViewer(new FeedbackDto(feedback)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // Get completed appointments for current patient that still need feedback
    @GetMapping("/patient/pending")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getPendingFeedbackForCurrentPatient() {
        try {
            Patient current = currentUserService.requirePatient();
            List<AppointmentResponse> pending = feedbackService
                    .getPendingFeedbackAppointmentsForPatient(current.getId());
            return ResponseEntity.ok(pending);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<?> getFeedbackByDoctor(@PathVariable Long doctorId) {
        try {
            List<FeedbackDto> dtos = feedbackService.getFeedbackByDoctor(doctorId).stream()
                    .map(FeedbackDto::new)
                    .map(this::forCurrentViewer)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("@access.isSelf(#patientId) or hasRole('ADMIN')")
    public ResponseEntity<?> getFeedbackByPatient(@PathVariable Long patientId) {
        try {
            List<Feedback> feedbacks = feedbackService.getFeedbackByPatient(patientId);
            List<FeedbackDto> dtos = feedbacks.stream()
                    .map(FeedbackDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/appointment/{appointmentId}")
    @PreAuthorize("@access.canAccessAppointment(#appointmentId)")
    public ResponseEntity<?> getFeedbackByAppointment(@PathVariable Long appointmentId) {
        try {
            Feedback feedback = feedbackService.getFeedbackByAppointment(appointmentId);
            return ResponseEntity.ok(forCurrentViewer(new FeedbackDto(feedback)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.ownsFeedback(#id)")
    public ResponseEntity<?> updateFeedback(@PathVariable Long id, @Valid @RequestBody CreateFeedbackRequest request) {
        try {
            Feedback feedback = new Feedback();
            feedback.setRating(request.getRating());
            feedback.setComment(request.getComment());

            Feedback updatedFeedback = feedbackService.updateFeedback(id, feedback);
            return ResponseEntity.ok(new FeedbackDto(updatedFeedback));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.ownsFeedback(#id)")
    public ResponseEntity<?> deleteFeedback(@PathVariable Long id) {
        try {
            feedbackService.deleteFeedback(id);
            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("message", "Feedback deleted successfully");
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
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
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/doctor/{doctorId}/rating-distribution")
    public ResponseEntity<?> getRatingDistributionByDoctor(@PathVariable Long doctorId) {
        try {
            Map<Integer, Long> ratingDistribution = feedbackService.getRatingDistributionByDoctor(doctorId);
            return ResponseEntity.ok(ratingDistribution);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    // Anonymous feedback keeps its author hidden from everyone but that patient and admins
    private FeedbackDto forCurrentViewer(FeedbackDto dto) {
        var viewer = currentUserService.current();
        if (viewer.isPresent() && viewer.get().role() == com.vikrant.careSync.entity.User.Role.ADMIN) {
            return dto;
        }
        return dto.forViewer(viewer.filter(v -> v.role() == com.vikrant.careSync.entity.User.Role.PATIENT)
                .map(v -> v.id()).orElse(null));
    }
}
