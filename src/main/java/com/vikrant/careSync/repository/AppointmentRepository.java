package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Appointment;
import com.vikrant.careSync.repository.projection.AppointmentParticipants;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

  @Query("""
      SELECT a.appointmentDateTime
      FROM Appointment a
      WHERE a.doctor.id = :doctorId
        AND a.isActive = true
        AND a.status IN ('BOOKED', 'CONFIRMED', 'SCHEDULED', 'IN_PROGRESS')
        AND a.appointmentDateTime >= :startOfDay
        AND a.appointmentDateTime < :nextDayStart
      """)
  List<LocalDateTime> findBookedSlotTimesByDoctorAndDate(
      @Param("doctorId") Long doctorId,
      @Param("startOfDay") LocalDateTime startOfDay,
      @Param("nextDayStart") LocalDateTime nextDayStart);

  @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.status = :status")
  List<Appointment> findByDoctorIdAndStatus(@Param("doctorId") Long doctorId,
      @Param("status") Appointment.Status status);

  @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.status = :status")
  List<Appointment> findByPatientIdAndStatus(@Param("patientId") Long patientId,
      @Param("status") Appointment.Status status);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH p.user JOIN FETCH a.doctor d JOIN FETCH d.user WHERE a.appointmentDateTime BETWEEN :startDate AND :endDate ORDER BY a.appointmentDateTime ASC")
  List<Appointment> findByAppointmentDateTimeBetweenWithDetails(@Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  // Enhanced queries with patient and doctor details
  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.doctor.id = :doctorId")
  List<Appointment> findByDoctorIdWithPatientAndDoctorDetails(@Param("doctorId") Long doctorId);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.patient.id = :patientId")
  List<Appointment> findByPatientIdWithPatientAndDoctorDetails(@Param("patientId") Long patientId);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.doctor.id = :doctorId AND a.appointmentDateTime > :now AND a.status IN ('BOOKED','SCHEDULED','CONFIRMED')")
  List<Appointment> findUpcomingAppointmentsByDoctorWithDetails(@Param("doctorId") Long doctorId,
      @Param("now") LocalDateTime now);

  // A plain range on appointment_date_time can use the (doctor_id, ..., appointment_date_time) index;
  // DATE(column) = DATE(:now) could not
  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.doctor.id = :doctorId "
      + "AND a.appointmentDateTime >= :dayStart AND a.appointmentDateTime < :nextDayStart")
  List<Appointment> findTodayAppointmentsByDoctorWithDetails(@Param("doctorId") Long doctorId,
      @Param("dayStart") LocalDateTime dayStart, @Param("nextDayStart") LocalDateTime nextDayStart);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d WHERE a.patient.id = :patientId AND a.appointmentDateTime > :now AND a.status = 'BOOKED'")
  List<Appointment> findUpcomingAppointmentsByPatientWithDetails(@Param("patientId") Long patientId,
      @Param("now") LocalDateTime now);

  // Health brief: the patient's coming appointments that are still on, soonest first
  @Query("SELECT a FROM Appointment a JOIN FETCH a.doctor WHERE a.patient.id = :patientId AND a.appointmentDateTime > :now"
      + " AND a.status IN ('BOOKED', 'REQUESTED', 'CONFIRMED', 'SCHEDULED', 'INTAKE', 'INTAKE_COMPLETED',"
      + " 'WAITING_ROOM', 'READY_FOR_VISIT') ORDER BY a.appointmentDateTime ASC")
  List<Appointment> findPatientComingAppointments(@Param("patientId") Long patientId,
      @Param("now") LocalDateTime now);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.id = :id")
  Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

  @Override
  Optional<Appointment> findById(Long id);

  // Access-control lookups (AccessGuard): ids only, no entity loading
  @Query("SELECT new com.vikrant.careSync.repository.projection.AppointmentParticipants(a.doctor.id, a.patient.id) FROM Appointment a WHERE a.id = :id")
  Optional<AppointmentParticipants> findParticipantsById(@Param("id") Long id);

  // A doctor "treats" a patient when they share at least one appointment that was not rejected or cancelled
  @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Appointment a WHERE a.doctor.id = :doctorId AND a.patient.id = :patientId"
      + " AND a.status NOT IN ('REJECTED', 'CANCELLED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR')")
  boolean existsByDoctorIdAndPatientId(@Param("doctorId") Long doctorId, @Param("patientId") Long patientId);

  @Query("SELECT DISTINCT a.patient.id FROM Appointment a WHERE a.doctor.id = :doctorId"
      + " AND a.status NOT IN ('REJECTED', 'CANCELLED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR')")
  List<Long> findTreatedPatientIds(@Param("doctorId") Long doctorId);

  // Doctor co-pilot: each treated patient with their latest appointment time with this doctor (rows: id, time)
  @Query("SELECT a.patient.id, MAX(a.appointmentDateTime) FROM Appointment a WHERE a.doctor.id = :doctorId"
      + " AND a.status NOT IN ('REJECTED', 'CANCELLED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR')"
      + " GROUP BY a.patient.id ORDER BY MAX(a.appointmentDateTime) DESC")
  List<Object[]> findTreatedPatientsWithLastAppointment(@Param("doctorId") Long doctorId);

  // Doctor co-pilot: the doctor's appointments in a time range that still stand, with their patients
  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient WHERE a.doctor.id = :doctorId"
      + " AND a.appointmentDateTime >= :from AND a.appointmentDateTime < :to"
      + " AND a.status NOT IN ('REJECTED', 'CANCELLED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR')"
      + " ORDER BY a.appointmentDateTime ASC")
  List<Appointment> findDoctorScheduleWithPatients(@Param("doctorId") Long doctorId,
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.id = :id")
  Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

  @Query("""
      SELECT COUNT(a)
      FROM Appointment a
      WHERE a.doctor.id = :doctorId
        AND a.isActive = true
        AND a.status IN ('BOOKED', 'SCHEDULED', 'CONFIRMED', 'IN_PROGRESS')
        AND a.appointmentDateTime > :startTime
        AND a.appointmentDateTime < :endTime
        AND (:excludedAppointmentId IS NULL OR a.id <> :excludedAppointmentId)
      """)
  long countConflictingAppointments(
      @Param("doctorId") Long doctorId,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("excludedAppointmentId") Long excludedAppointmentId);

  @Override
  <S extends Appointment> S save(S entity);

  @Override
  void deleteById(Long id);

  @Modifying
  @Query("UPDATE Appointment a SET a.isActive = false WHERE a.isActive = true AND a.appointmentDateTime < :threshold AND a.status = 'BOOKED'")
  int deactivateExpiredAppointments(@Param("threshold") LocalDateTime threshold);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.isActive = true AND a.reminder24hSent = false AND a.appointmentDateTime BETWEEN :startWindow AND :endWindow AND a.status IN ('BOOKED', 'SCHEDULED', 'CONFIRMED')")
  List<Appointment> findAppointmentsNeeding24hReminder(@Param("startWindow") LocalDateTime startWindow,
      @Param("endWindow") LocalDateTime endWindow);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.isActive = true AND a.reminder1hSent = false AND a.appointmentDateTime BETWEEN :startWindow AND :endWindow AND a.status IN ('BOOKED', 'SCHEDULED', 'CONFIRMED')")
  List<Appointment> findAppointmentsNeeding1hReminder(@Param("startWindow") LocalDateTime startWindow,
      @Param("endWindow") LocalDateTime endWindow);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.isActive = true AND a.reminder10mSent = false AND a.appointmentDateTime BETWEEN :startWindow AND :endWindow AND a.status IN ('BOOKED', 'SCHEDULED', 'CONFIRMED')")
  List<Appointment> findAppointmentsNeeding10mReminder(@Param("startWindow") LocalDateTime startWindow,
      @Param("endWindow") LocalDateTime endWindow);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.isActive = true AND a.appointmentDateTime < :cutoffTime AND a.status IN ('BOOKED', 'SCHEDULED', 'CONFIRMED', 'INTAKE_COMPLETED', 'READY_FOR_VISIT')")
  List<Appointment> findUnstartedPastAppointments(@Param("cutoffTime") LocalDateTime cutoffTime);

  @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.doctor WHERE a.isActive = true AND a.status = 'IN_PROGRESS' AND a.updatedAt < :cutoffTime")
  List<Appointment> findOrphanedInProgressAppointments(@Param("cutoffTime") LocalDateTime cutoffTime);

  // ---- Doctor dashboard: aggregates instead of loading every appointment ----
  @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId "
      + "AND a.appointmentDateTime >= :start AND a.appointmentDateTime < :end")
  long countByDoctorIdBetween(@Param("doctorId") Long doctorId, @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.status IN :statuses")
  long countByDoctorIdAndStatusIn(@Param("doctorId") Long doctorId,
      @Param("statuses") java.util.Collection<Appointment.Status> statuses);

  /** One row per patient: [patientId, first appointment date-time]. */
  @Query("SELECT a.patient.id, MIN(a.appointmentDateTime) FROM Appointment a WHERE a.doctor.id = :doctorId "
      + "AND a.appointmentDateTime IS NOT NULL GROUP BY a.patient.id")
  List<Object[]> findFirstVisitPerPatient(@Param("doctorId") Long doctorId);

  /** Appointments whose status may differ from what it was at :at (changed later, or logged since :logsSince). */
  @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND ("
      + "COALESCE(a.statusChangedAt, a.createdAt) > :at "
      + "OR a.id IN (SELECT l.appointmentId FROM AppointmentStatusLog l WHERE l.createdAt >= :logsSince))")
  List<Appointment> findStatusChangedSince(@Param("doctorId") Long doctorId, @Param("at") LocalDateTime at,
      @Param("logsSince") LocalDateTime logsSince);

  // ---- Doctor's appointment pages: filter, sort and page in SQL (no search term) ----
  // Order matches the old in-memory sort: upcoming soonest first, then past most recent first.
  @Query(value = "SELECT a FROM Appointment a JOIN FETCH a.patient p JOIN FETCH a.doctor d "
      + "WHERE a.doctor.id = :doctorId AND (:allStatuses = true OR a.status IN :statuses) "
      + "AND a.appointmentDateTime >= :from AND a.appointmentDateTime < :to "
      + "ORDER BY CASE WHEN a.appointmentDateTime > :now THEN 0 ELSE 1 END, "
      + "CASE WHEN a.appointmentDateTime > :now THEN a.appointmentDateTime END ASC, "
      + "a.appointmentDateTime DESC, a.id",
      countQuery = "SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId "
          + "AND (:allStatuses = true OR a.status IN :statuses) "
          + "AND a.appointmentDateTime >= :from AND a.appointmentDateTime < :to")
  org.springframework.data.domain.Page<Appointment> findDoctorAppointmentPage(@Param("doctorId") Long doctorId,
      @Param("allStatuses") boolean allStatuses,
      @Param("statuses") java.util.Collection<Appointment.Status> statuses,
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("now") LocalDateTime now,
      org.springframework.data.domain.Pageable pageable);

  // ---- Reports: aggregates over a date range ----
  @Query("SELECT a.status, COUNT(a) FROM Appointment a WHERE a.appointmentDateTime >= :start "
      + "AND a.appointmentDateTime < :end GROUP BY a.status")
  List<Object[]> countByStatusBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query("SELECT extract(date from a.appointmentDateTime), COUNT(a) FROM Appointment a "
      + "WHERE a.appointmentDateTime >= :start AND a.appointmentDateTime < :end "
      + "GROUP BY extract(date from a.appointmentDateTime) ORDER BY extract(date from a.appointmentDateTime)")
  List<Object[]> countPerDayBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /** One row: [distinct doctors, distinct patients] with appointments in the range. */
  @Query("SELECT COUNT(DISTINCT a.doctor.id), COUNT(DISTINCT a.patient.id) FROM Appointment a "
      + "WHERE a.appointmentDateTime >= :start AND a.appointmentDateTime < :end")
  List<Object[]> countActiveDoctorsAndPatientsBetween(@Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /** [doctorId, number of appointments] for every doctor with appointments. */
  @Query("SELECT a.doctor.id, COUNT(a) FROM Appointment a GROUP BY a.doctor.id")
  List<Object[]> countPerDoctor();
}
