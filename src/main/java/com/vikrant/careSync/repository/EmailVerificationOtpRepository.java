package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.EmailVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationOtpRepository extends JpaRepository<EmailVerificationOtp, Long> {

    Optional<EmailVerificationOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    Optional<EmailVerificationOtp> findTopByEmailAndVerifiedTrueOrderByCreatedAtDesc(String email);

    @Query("SELECT e FROM EmailVerificationOtp e WHERE e.email = :email AND e.otp = :otp AND e.used = false AND e.expiryDate > :now ORDER BY e.createdAt DESC")
    Optional<EmailVerificationOtp> findValidOtp(String email, String otp, LocalDateTime now);
}