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

    /** Used when issuing a new code, so only the newest code for an email can ever match. */
    @Modifying
    @Query("DELETE FROM PasswordResetOtp p WHERE p.email = :email")
    void deleteAllForEmail(@Param("email") String email);

    // Retention: used codes and long-expired ones
    @Modifying
    @Query("DELETE FROM PasswordResetOtp p WHERE (p.used = true AND p.createdAt < :cutoff) OR p.expiryDate < :cutoff")
    int deleteSpentBefore(@Param("cutoff") Instant cutoff);
}
