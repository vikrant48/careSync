package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Doctor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @Query("SELECT d FROM Doctor d WHERE LOWER(d.user.username) = LOWER(:username)")
    Optional<Doctor> findByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Doctor d WHERE LOWER(d.user.username) = LOWER(:username)")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT d FROM Doctor d WHERE d.user.email = :email")
    Optional<Doctor> findByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Doctor d WHERE d.user.email = :email")
    boolean existsByEmail(@Param("email") String email);

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
