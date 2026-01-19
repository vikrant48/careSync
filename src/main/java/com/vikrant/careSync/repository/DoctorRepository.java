package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<Doctor> findByEmail(String email);

    boolean existsByEmail(String email);

    @Override
    Optional<Doctor> findById(Long id);

    @Override
    <S extends Doctor> S save(S entity);

    @Override
    void deleteById(Long id);
}