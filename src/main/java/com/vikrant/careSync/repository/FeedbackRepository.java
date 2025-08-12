package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByDoctorId(Long doctorId);
    List<Feedback> findByPatientId(Long patientId);
    
    @Query("SELECT f FROM Feedback f WHERE f.appointment.id = :appointmentId")
    Optional<Feedback> findByAppointmentId(@Param("appointmentId") Long appointmentId);
    
    @Query("SELECT f FROM Feedback f WHERE f.doctor.id = :doctorId ORDER BY f.createdAt DESC")
    List<Feedback> findByDoctorIdOrderByCreatedAtDesc(@Param("doctorId") Long doctorId);
    
    @Query("SELECT f FROM Feedback f WHERE f.doctor.id = :doctorId AND f.rating >= :rating")
    List<Feedback> findByDoctorIdAndRatingGreaterThanEqual(@Param("doctorId") Long doctorId, @Param("rating") int rating);
    
    @Query("SELECT f FROM Feedback f WHERE f.doctor.id = :doctorId AND f.rating <= :rating")
    List<Feedback> findByDoctorIdAndRatingLessThanEqual(@Param("doctorId") Long doctorId, @Param("rating") int rating);
    
    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.doctor.id = :doctorId")
    long countByDoctorId(@Param("doctorId") Long doctorId);
    
    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.doctor.id = :doctorId AND f.rating = :rating")
    long countByDoctorIdAndRating(@Param("doctorId") Long doctorId, @Param("rating") int rating);
} 