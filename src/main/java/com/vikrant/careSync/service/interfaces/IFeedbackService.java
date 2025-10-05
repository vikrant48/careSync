package com.vikrant.careSync.service.interfaces;

import com.vikrant.careSync.entity.Feedback;
import com.vikrant.careSync.dto.PatientAppointmentResponse;

import java.util.List;
import java.util.Map;

/**
 * Service interface for Feedback operations
 * Defines all business logic operations related to feedback
 */
public interface IFeedbackService {

    /**
     * Create new feedback
     * @param feedback Feedback to create
     * @return Created feedback
     */
    Feedback createFeedback(Feedback feedback);

    /**
     * Get feedback by ID
     * @param id Feedback ID
     * @return Feedback
     */
    Feedback getFeedbackById(Long id);

    /**
     * Get feedback by doctor
     * @param doctorId Doctor ID
     * @return List of feedbacks for the doctor
     */
    List<Feedback> getFeedbackByDoctor(Long doctorId);

    /**
     * Get feedback by patient
     * @param patientId Patient ID
     * @return List of feedbacks by the patient
     */
    List<Feedback> getFeedbackByPatient(Long patientId);

    /**
     * Get feedback by appointment
     * @param appointmentId Appointment ID
     * @return Feedback for the appointment
     */
    Feedback getFeedbackByAppointment(Long appointmentId);

    /**
     * Update feedback
     * @param feedbackId Feedback ID
     * @param updatedFeedback Updated feedback
     * @return Updated feedback
     */
    Feedback updateFeedback(Long feedbackId, Feedback updatedFeedback);

    /**
     * Delete feedback
     * @param feedbackId Feedback ID
     */
    void deleteFeedback(Long feedbackId);

    /**
     * Submit feedback for an appointment
     * @param appointmentId Appointment ID
     * @param doctorId Doctor ID (optional)
     * @param rating Rating (1-5)
     * @param comment Comment
     * @param anonymous Whether feedback is anonymous
     * @return Created feedback
     */
    Feedback submitFeedback(Long appointmentId, Long doctorId, int rating, String comment, Boolean anonymous);

    /**
     * Submit feedback for an appointment (legacy method)
     * @param appointmentId Appointment ID
     * @param patientId Patient ID
     * @param rating Rating (1-5)
     * @param comment Comment
     * @return Created feedback
     */
    Feedback submitFeedback(Long appointmentId, Long patientId, int rating, String comment);

    /**
     * Get average rating by doctor
     * @param doctorId Doctor ID
     * @return Average rating
     */
    double getAverageRatingByDoctor(Long doctorId);

    /**
     * Get rating distribution by doctor
     * @param doctorId Doctor ID
     * @return Map of rating to count
     */
    Map<Integer, Long> getRatingDistributionByDoctor(Long doctorId);

    /**
     * Get recent feedbacks for a doctor
     * @param doctorId Doctor ID
     * @param limit Number of feedbacks to retrieve
     * @return List of recent feedbacks
     */
    List<Feedback> getRecentFeedbacks(Long doctorId, int limit);

    /**
     * Get high rated feedbacks (rating >= 4)
     * @param doctorId Doctor ID
     * @return List of high rated feedbacks
     */
    List<Feedback> getHighRatedFeedbacks(Long doctorId);

    /**
     * Get completed appointments for a patient that have no feedback yet
     * @param patientId Patient ID
     * @return List of appointments ready for feedback (patient view DTO)
     */
    List<PatientAppointmentResponse> getPendingFeedbackAppointmentsForPatient(Long patientId);

    /**
     * Get low rated feedbacks (rating <= 2)
     * @param doctorId Doctor ID
     * @return List of low rated feedbacks
     */
    List<Feedback> getLowRatedFeedbacks(Long doctorId);

    /**
     * Get total feedbacks count for a doctor
     * @param doctorId Doctor ID
     * @return Total count of feedbacks
     */
    long getTotalFeedbacksCount(Long doctorId);

    /**
     * Get feedbacks count by rating for a doctor
     * @param doctorId Doctor ID
     * @param rating Rating value
     * @return Count of feedbacks with the specified rating
     */
    long getFeedbacksCountByRating(Long doctorId, int rating);
}