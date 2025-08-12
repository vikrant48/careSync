package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.Patient;
import com.vikrant.careSync.repository.FeedbackRepository;
import com.vikrant.careSync.repository.AppointmentRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    public Feedback createFeedback(Feedback feedback) {
        // Validate feedback
        if (feedback.getAppointment() == null || feedback.getAppointment().getId() == null) {
            throw new RuntimeException("Appointment is required");
        }
        if (feedback.getPatient() == null || feedback.getPatient().getId() == null) {
            throw new RuntimeException("Patient is required");
        }
        if (feedback.getDoctor() == null || feedback.getDoctor().getId() == null) {
            throw new RuntimeException("Doctor is required");
        }
        if (feedback.getRating() < 1 || feedback.getRating() > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        // Check if feedback already exists for this appointment
        if (feedbackRepository.findByAppointmentId(feedback.getAppointment().getId()).isPresent()) {
            throw new RuntimeException("Feedback already exists for this appointment");
        }

        // Set creation time
        feedback.setCreatedAt(LocalDateTime.now());

        return feedbackRepository.save(feedback);
    }

    public Feedback getFeedbackById(Long id) {
        return feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found"));
    }

    public List<Feedback> getFeedbackByDoctor(Long doctorId) {
        return feedbackRepository.findByDoctorId(doctorId);
    }

    public List<Feedback> getFeedbackByPatient(Long patientId) {
        return feedbackRepository.findByPatientId(patientId);
    }

    public Feedback getFeedbackByAppointment(Long appointmentId) {
        return feedbackRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new RuntimeException("Feedback not found for this appointment"));
    }

    public Feedback updateFeedback(Long id, Feedback updatedFeedback) {
        Feedback existingFeedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found"));

        // Update fields if provided
        if (updatedFeedback.getRating() > 0) {
            if (updatedFeedback.getRating() < 1 || updatedFeedback.getRating() > 5) {
                throw new RuntimeException("Rating must be between 1 and 5");
            }
            existingFeedback.setRating(updatedFeedback.getRating());
        }

        if (updatedFeedback.getComment() != null) {
            existingFeedback.setComment(updatedFeedback.getComment());
        }

        return feedbackRepository.save(existingFeedback);
    }

    public void deleteFeedback(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found"));
        feedbackRepository.delete(feedback);
    }

    public double getAverageRatingByDoctor(Long doctorId) {
        List<Feedback> feedbacks = feedbackRepository.findByDoctorId(doctorId);
        if (feedbacks.isEmpty()) {
            return 0.0;
        }
        
        double totalRating = feedbacks.stream()
                .mapToInt(Feedback::getRating)
                .sum();
        
        return (double) totalRating / feedbacks.size();
    }

    public Map<Integer, Long> getRatingDistributionByDoctor(Long doctorId) {
        List<Feedback> feedbacks = feedbackRepository.findByDoctorId(doctorId);
        
        return feedbacks.stream()
                .collect(Collectors.groupingBy(
                    Feedback::getRating,
                    Collectors.counting()
                ));
    }

    public Feedback submitFeedback(Long appointmentId, Long patientId, int rating, String comment) {
        // Validate appointment exists and belongs to patient
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Appointment does not belong to this patient");
        }

        // Check if feedback already exists
        if (feedbackRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new RuntimeException("Feedback already exists for this appointment");
        }

        // Validate rating
        if (rating < 1 || rating > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        // Create feedback
        Feedback feedback = new Feedback();
        feedback.setAppointment(appointment);
        feedback.setPatient(appointment.getPatient());
        feedback.setDoctor(appointment.getDoctor());
        feedback.setRating(rating);
        feedback.setComment(comment);
        feedback.setCreatedAt(LocalDateTime.now());

        return feedbackRepository.save(feedback);
    }

    public List<Feedback> getRecentFeedbacks(Long doctorId, int limit) {
        return feedbackRepository.findByDoctorIdOrderByCreatedAtDesc(doctorId)
                .stream()
                .limit(limit)
                .toList();
    }

    public List<Feedback> getHighRatedFeedbacks(Long doctorId) {
        return feedbackRepository.findByDoctorIdAndRatingGreaterThanEqual(doctorId, 4);
    }

    public List<Feedback> getLowRatedFeedbacks(Long doctorId) {
        return feedbackRepository.findByDoctorIdAndRatingLessThanEqual(doctorId, 2);
    }

    public long getTotalFeedbacksCount(Long doctorId) {
        return feedbackRepository.countByDoctorId(doctorId);
    }

    public long getFeedbacksCountByRating(Long doctorId, int rating) {
        return feedbackRepository.countByDoctorIdAndRating(doctorId, rating);
    }
} 