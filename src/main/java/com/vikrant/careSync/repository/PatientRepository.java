package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Patient;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<Patient> findByEmail(String email);

    boolean existsByEmail(String email);

    @Override
    Optional<Patient> findById(Long id);

    @Override
    <S extends Patient> S save(S entity);

    @Override
    void deleteById(Long id);
}