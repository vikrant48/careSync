package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("SELECT p FROM Patient p WHERE LOWER(p.user.username) = LOWER(:username)")
    Optional<Patient> findByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Patient p WHERE LOWER(p.user.username) = LOWER(:username)")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT p FROM Patient p WHERE p.user.email = :email")
    Optional<Patient> findByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Patient p WHERE p.user.email = :email")
    boolean existsByEmail(@Param("email") String email);

    @Override
    Optional<Patient> findById(Long id);

    @Override
    <S extends Patient> S save(S entity);

    @Override
    void deleteById(Long id);
}