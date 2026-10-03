package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Doctor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.vikrant.careSync.dto.AdminDoctorListItemDto;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long>, DoctorRepositoryCustom {

    @Query("SELECT new com.vikrant.careSync.dto.AdminDoctorListItemDto(d.id, u.username, d.firstName, d.lastName, d.specialization, d.isVerified, d.isActive) "
            +
            "FROM Doctor d JOIN d.user u " +
            "WHERE (:search IS NULL OR :search = '' OR LOWER(d.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(d.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', :search, '%'))) "
            +
            "AND (:isVerified IS NULL OR d.isVerified = :isVerified) " +
            "ORDER BY d.id DESC")
    Page<AdminDoctorListItemDto> findAdminDoctorList(@Param("search") String search,
            @Param("isVerified") Boolean isVerified, Pageable pageable);

    long countByIsVerified(boolean isVerified);

    @Query("SELECT DISTINCT d FROM Doctor d LEFT JOIN FETCH d.user")
    java.util.List<Doctor> findAllWithUser();

    @Query("SELECT d FROM Doctor d LEFT JOIN FETCH d.user WHERE LOWER(d.user.username) = LOWER(:username)")
    Optional<Doctor> findByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Doctor d WHERE LOWER(d.user.username) = LOWER(:username)")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT d FROM Doctor d LEFT JOIN FETCH d.user WHERE d.user.email = :email")
    Optional<Doctor> findByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Doctor d WHERE d.user.email = :email")
    boolean existsByEmail(@Param("email") String email);

    @Override
    @Query("SELECT d FROM Doctor d LEFT JOIN FETCH d.user WHERE d.id = :id")
    Optional<Doctor> findById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    @Override
    <S extends Doctor> S save(S entity);

    @Override
    void deleteById(Long id);
}
