package com.bank.shared.model;

import java.time.LocalDateTime;

public class NotificationStatus {

    public enum Status {
        PENDING, SENT, FAILED
    }

    private String notificationId;
    private Status status;
    private String channel;
    private LocalDateTime sentAt;
    private String errorMessage;

    public NotificationStatus() {}

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
