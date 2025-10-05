package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientTypeAndRecipientIdOrderByTimestampDesc(String recipientType, Long recipientId);

    long countByRecipientTypeAndRecipientIdAndReadIsFalse(String recipientType, Long recipientId);

    // Mark read will be handled in service by loading and saving the entity
}