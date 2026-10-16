package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // Access-control lookup (AccessGuard): authoring patient id only
    @Query("SELECT f.patient.id FROM Feedback f WHERE f.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);

    List<Feedback> findByDoctorId(Long doctorId);

    List<Feedback> findByPatientId(Long patientId);

    @Query("SELECT f FROM Feedback f WHERE f.appointment.id = :appointmentId")
    Optional<Feedback> findByAppointmentId(@Param("appointmentId") Long appointmentId);

    @Query("SELECT f FROM Feedback f WHERE f.doctor.id = :doctorId ORDER BY f.createdAt DESC")
    List<Feedback> findByDoctorIdOrderByCreatedAtDesc(@Param("doctorId") Long doctorId);

    @Query("SELECT f FROM Feedback f WHERE f.doctor.id = :doctorId AND f.rating >= :rating")
    List<Feedback> findByDoctorIdAndRatingGreaterThanEqual(@Param("doctorId") Long doctorId,
            @Param("rating") int rating);

    @Query("SELECT f FROM Feedback f WHERE f.doctor.id = :doctorId AND f.rating <= :rating")
    List<Feedback> findByDoctorIdAndRatingLessThanEqual(@Param("doctorId") Long doctorId, @Param("rating") int rating);

    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.doctor.id = :doctorId")
    long countByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.doctor.id = :doctorId AND f.rating = :rating")
    long countByDoctorIdAndRating(@Param("doctorId") Long doctorId, @Param("rating") int rating);

    @Query("SELECT f.doctor.id, AVG(CAST(f.rating AS double)), COUNT(f) FROM Feedback f WHERE f.doctor.id IN :doctorIds GROUP BY f.doctor.id")
    List<Object[]> findDoctorRatingAggregatesIn(@Param("doctorIds") List<Long> doctorIds);

    @Override

    Optional<Feedback> findById(Long id);

    @Override
    <S extends Feedback> S save(S entity);

    @Override
    void deleteById(Long id);

    // ---- Doctor dashboard aggregates ----
    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.doctor.id = :doctorId")
    long countByDoctorIdValue(@Param("doctorId") Long doctorId);

    /** Null when there is no feedback in the range. */
    @Query("SELECT AVG(f.rating) FROM Feedback f WHERE f.doctor.id = :doctorId "
            + "AND f.createdAt >= :start AND f.createdAt < :end")
    Double averageRatingBetween(@Param("doctorId") Long doctorId, @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    /** Which of these appointments already have feedback (one query for a whole list). */
    @Query("SELECT f.appointment.id FROM Feedback f WHERE f.appointment.id IN :appointmentIds")
    List<Long> findAppointmentIdsWithFeedback(@Param("appointmentIds") java.util.Collection<Long> appointmentIds);

    /** [doctorId, average rating] for every doctor with feedback. */
    @Query("SELECT f.doctor.id, AVG(f.rating) FROM Feedback f WHERE f.doctor IS NOT NULL GROUP BY f.doctor.id")
    List<Object[]> averageRatingPerDoctor();

    /** Null when there is no feedback at all. */
    @Query("SELECT AVG(f.rating) FROM Feedback f")
    Double averageRatingOverall();
}
