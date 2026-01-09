package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {
    List<DoctorLeave> findByDoctorId(Long doctorId);

    @Query("SELECT dl FROM DoctorLeave dl WHERE dl.doctor.id = :doctorId AND (dl.startDate <= :date AND dl.endDate >= :date)")
    List<DoctorLeave> findActiveLeavesByDoctorAndDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    @Query("SELECT dl FROM DoctorLeave dl WHERE dl.doctor.id = :doctorId AND dl.endDate >= :today")
    List<DoctorLeave> findUpcomingLeavesByDoctor(@Param("doctorId") Long doctorId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(dl) > 0 FROM DoctorLeave dl WHERE dl.doctor.id = :doctorId AND (dl.startDate <= :date AND dl.endDate >= :date)")
    boolean isDoctorOnLeave(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
}
