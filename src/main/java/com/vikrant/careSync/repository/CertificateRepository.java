package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Certificate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    
    @Query("SELECT c FROM Certificate c WHERE c.doctor.id = :doctorId")
    List<Certificate> findByDoctorId(@Param("doctorId") Long doctorId);
    
    @Override
    Optional<Certificate> findById(Long id);
    
    @Override
    <S extends Certificate> S save(S entity);
    
    @Override
    void deleteById(Long id);
}