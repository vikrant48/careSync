package com.vikrant.careSync.security.repository;

import java.time.Instant;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.vikrant.careSync.security.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByEmail(String email);

    // Retention: reset links live minutes; anything expired before the cutoff goes
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiryDate < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
