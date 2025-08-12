package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId")
    List<Appointment> findByDoctorId(@Param("doctorId") Long doctorId);
    
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId")
    List<Appointment> findByPatientId(@Param("patientId") Long patientId);
    
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDateTime BETWEEN :startDate AND :endDate")
    List<Appointment> findByDoctorIdAndAppointmentDateTimeBetween(
            @Param("doctorId") Long doctorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.status = :status")
    List<Appointment> findByDoctorIdAndStatus(@Param("doctorId") Long doctorId, @Param("status") Appointment.Status status);
    
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.status = :status")
    List<Appointment> findByPatientIdAndStatus(@Param("patientId") Long patientId, @Param("status") Appointment.Status status);
    
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.appointmentDateTime BETWEEN :startDate AND :endDate")
    List<Appointment> findByPatientIdAndAppointmentDateTimeBetween(
            @Param("patientId") Long patientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    // Enhanced queries with patient and doctor details
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.doctor.id = :doctorId")
    List<Appointment> findByDoctorIdWithPatientAndDoctorDetails(@Param("doctorId") Long doctorId);
    
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.patient.id = :patientId")
    List<Appointment> findByPatientIdWithPatientAndDoctorDetails(@Param("patientId") Long patientId);
    
    // Query for upcoming appointments with details
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.doctor.id = :doctorId AND a.appointmentDateTime > :now AND a.status = 'BOOKED'")
    List<Appointment> findUpcomingAppointmentsByDoctorWithDetails(@Param("doctorId") Long doctorId, @Param("now") LocalDateTime now);
    
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.patient.id = :patientId AND a.appointmentDateTime > :now AND a.status = 'BOOKED'")
    List<Appointment> findUpcomingAppointmentsByPatientWithDetails(@Param("patientId") Long patientId, @Param("now") LocalDateTime now);
} 