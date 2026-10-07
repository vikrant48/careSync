package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AppointmentIntake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppointmentIntakeRepository extends JpaRepository<AppointmentIntake, Long> {
    Optional<AppointmentIntake> findByAppointmentId(Long appointmentId);
}
