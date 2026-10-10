package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AppointmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentStatusLogRepository extends JpaRepository<AppointmentStatusLog, Long> {
    List<AppointmentStatusLog> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);

    @Query("""
            SELECT l FROM AppointmentStatusLog l
            WHERE l.appointmentId IN :appointmentIds
              AND l.createdAt >= :since
            """)
    List<AppointmentStatusLog> findByAppointmentIdInAndCreatedAtGreaterThanEqual(
            @Param("appointmentIds") List<Long> appointmentIds,
            @Param("since") LocalDateTime since);
}
