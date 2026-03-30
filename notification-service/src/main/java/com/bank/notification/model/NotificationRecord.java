package com.bank.notification.model;

import com.bank.shared.model.NotificationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document representing a notification attempt and its outcome.
 * Persisted in the 'notifications' collection.
 */
@Document(collection = "notifications")
public class NotificationRecord {

    @Id
    private String notificationId;
    private String recipientId;
    private String channel;       // EMAIL, SMS, PUSH
    private String subject;
    private String body;
    private String correlationId;
    private NotificationStatus.Status status;
    private String errorMessage;
    private int attemptCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NotificationRecord() {}

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public NotificationStatus.Status getStatus() { return status; }
    public void setStatus(NotificationStatus.Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
