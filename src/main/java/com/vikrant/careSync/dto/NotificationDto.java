package com.vikrant.careSync.dto;

import com.vikrant.careSync.entity.Notification;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String recipientType;
    private Long recipientId;
    private String title;
    private String message;
    private String type;
    private Boolean read;
    private LocalDateTime timestamp;
    private String link;

    public NotificationDto(Notification notification) {
        this.id = notification.getId();
        this.recipientType = notification.getRecipientType();
        this.recipientId = notification.getRecipientId();
        this.title = notification.getTitle();
        this.message = notification.getMessage();
        this.type = notification.getType();
        this.read = notification.getRead();
        this.timestamp = notification.getTimestamp();
        this.link = notification.getLink();
    }
}
