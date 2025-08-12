package com.vikrant.careSync.security.repository;

import com.vikrant.careSync.security.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    
    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.username = :username AND la.successful = false AND la.timestamp > :since")
    long countFailedAttemptsByUsername(@Param("username") String username, @Param("since") Instant since);
    
    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.ipAddress = :ipAddress AND la.successful = false AND la.timestamp > :since")
    long countFailedAttemptsByIP(@Param("ipAddress") String ipAddress, @Param("since") Instant since);
    
    List<LoginAttempt> findByUsernameOrderByTimestampDesc(String username);
    
    List<LoginAttempt> findByIpAddressOrderByTimestampDesc(String ipAddress);
} 