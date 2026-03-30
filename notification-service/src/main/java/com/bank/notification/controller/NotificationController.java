package com.bank.notification.controller;

import com.bank.notification.service.NotificationServiceImpl;
import com.bank.shared.model.NotificationStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for notification status queries.
 * Notification sending is triggered asynchronously via Kafka consumers.
 */
@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationServiceImpl notificationService;

    public NotificationController(NotificationServiceImpl notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /v1/notifications/{notificationId}/status
     */
    @GetMapping("/{notificationId}/status")
    public ResponseEntity<NotificationStatus> getStatus(@PathVariable String notificationId) {
        NotificationStatus status = notificationService.getStatus(notificationId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
