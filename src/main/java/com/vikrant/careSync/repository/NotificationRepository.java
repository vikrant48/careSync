package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByRecipientTypeAndRecipientIdAndReadIsFalse(String recipientType, Long recipientId);

    // The feed is polled: newest 100 only (index: recipient_type, recipient_id, timestamp DESC)
    List<Notification> findTop100ByRecipientTypeAndRecipientIdOrderByTimestampDesc(String recipientType,
            Long recipientId);

    // Mark read will be handled in service by loading and saving the entity
}