package com.vikrant.careSync.security.repository;

import org.springframework.data.jpa.repository.Modifying;
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
    
    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.username = :username AND la.ipAddress = :ipAddress AND la.successful = false AND la.timestamp > :since")
    long countFailedAttemptsByUsernameAndIP(@Param("username") String username, @Param("ipAddress") String ipAddress,
            @Param("since") Instant since);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.ipAddress = :ipAddress AND la.successful = false AND la.timestamp > :since")
    long countFailedAttemptsByIP(@Param("ipAddress") String ipAddress, @Param("since") Instant since);
    
    

    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
