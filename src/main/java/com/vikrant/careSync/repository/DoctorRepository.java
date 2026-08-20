package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Doctor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<Doctor> findByEmail(String email);

    boolean existsByEmail(String email);

    @Override
    Optional<Doctor> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    @Override
    <S extends Doctor> S save(S entity);

    @Override
    void deleteById(Long id);
}
