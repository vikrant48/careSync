package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.AiConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    Optional<AiConversation> findByPublicId(String publicId);

    Optional<AiConversation> findByPublicIdAndUserId(String publicId, Long userId);

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** Messages go with their conversation (ON DELETE CASCADE) */
    @Modifying
    @Query("DELETE FROM AiConversation c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /** Retention: chats not used since the cut-off (messages go with them) */
    @Modifying
    @Query("DELETE FROM AiConversation c WHERE c.updatedAt < :cutoff")
    int deleteNotUpdatedSince(@Param("cutoff") LocalDateTime cutoff);
}
