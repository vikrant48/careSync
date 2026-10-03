package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AppointmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentStatusLogRepository extends JpaRepository<AppointmentStatusLog, Long> {
    List<AppointmentStatusLog> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);
}
