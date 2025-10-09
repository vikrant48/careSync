package com.vikrant.careSync.security.repository;

import com.vikrant.careSync.security.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    Optional<PasswordResetOtp> findByEmailAndOtp(String email, String otp);

    @Modifying
    @Query("DELETE FROM PasswordResetOtp p WHERE p.email = :email AND p.expiryDate < :now")
    void deleteExpiredForEmail(@Param("email") String email, @Param("now") Instant now);
}