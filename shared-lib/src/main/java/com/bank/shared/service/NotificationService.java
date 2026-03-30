package com.bank.shared.service;

import com.bank.shared.model.NotificationStatus;

public interface NotificationService {

    record NotificationRequest(
        String recipientId,
        String channel,
        String subject,
        String body,
        String correlationId
    ) {}

    void sendNotification(NotificationRequest request);

    NotificationStatus getStatus(String notificationId);
}
