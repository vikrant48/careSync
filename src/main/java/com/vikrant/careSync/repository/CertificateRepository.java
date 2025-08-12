package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    @Query("SELECT c FROM Certificate c WHERE c.doctor.id = :doctorId")
    List<Certificate> findByDoctorId(@Param("doctorId") Long doctorId);
} 