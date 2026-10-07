package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AppointmentSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppointmentSignatureRepository extends JpaRepository<AppointmentSignature, Long> {
    Optional<AppointmentSignature> findByAppointmentId(Long appointmentId);
}
