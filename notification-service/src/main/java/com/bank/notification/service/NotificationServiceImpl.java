package com.bank.notification.service;

import com.bank.notification.channel.EmailChannel;
import com.bank.notification.channel.SmsAndPushChannel;
import com.bank.notification.model.NotificationRecord;
import com.bank.notification.repository.NotificationRepository;
import com.bank.shared.model.NotificationStatus;
import com.bank.shared.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Service implementation.
 *
 * Consumes events from Kafka topics (transactions.completed, accounts.events)
 * and dispatches notifications via the appropriate channel (email/SMS/push).
 *
 * Guarantees:
 * - Completely asynchronous — does not block the transaction critical path (Req 5.4)
 * - Persists result (SENT/FAILED) in MongoDB (Req 5.2)
 * - Retries with exponential backoff before marking FAILED (Req 5.3)
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_SMS   = "SMS";
    private static final String CHANNEL_PUSH  = "PUSH";

    private final NotificationRepository notificationRepository;
    private final EmailChannel emailChannel;
    private final SmsAndPushChannel smsAndPushChannel;

    @Value("${notification.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${notification.retry.initial-interval-ms:1000}")
    private long initialIntervalMs;

    @Value("${notification.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${notification.retry.max-interval-ms:10000}")
    private long maxIntervalMs;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   EmailChannel emailChannel,
                                   SmsAndPushChannel smsAndPushChannel) {
        this.notificationRepository = notificationRepository;
        this.emailChannel = emailChannel;
        this.smsAndPushChannel = smsAndPushChannel;
    }

    // -------------------------------------------------------------------------
    // Kafka Consumers (Req 5.1, 5.4)
    // -------------------------------------------------------------------------

    /**
     * Consumes TransactionCompleted events and triggers notifications.
     * Runs on a virtual thread (configured via VirtualThreadConfig).
     */
    @KafkaListener(
        topics = "transactions.completed",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionCompleted(ConsumerRecord<String, Map<String, Object>> record,
                                       Acknowledgment ack) {
        log.info("Received transaction.completed event key={}", record.key());
        try {
            Map<String, Object> payload = record.value();
            NotificationRequest request = buildRequestFromTransactionEvent(payload);
            sendNotification(request);
        } catch (Exception e) {
            log.error("Failed to process transaction.completed event key={}", record.key(), e);
        } finally {
            ack.acknowledge();
        }
    }

    /**
     * Consumes account lifecycle events and triggers notifications.
     */
    @KafkaListener(
        topics = "accounts.events",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAccountEvent(ConsumerRecord<String, Map<String, Object>> record,
                               Acknowledgment ack) {
        log.info("Received accounts.events event key={}", record.key());
        try {
            Map<String, Object> payload = record.value();
            NotificationRequest request = buildRequestFromAccountEvent(payload);
            sendNotification(request);
        } catch (Exception e) {
            log.error("Failed to process accounts.events event key={}", record.key(), e);
        } finally {
            ack.acknowledge();
        }
    }

    // -------------------------------------------------------------------------
    // NotificationService interface (Req 5.1, 5.2, 5.3)
    // -------------------------------------------------------------------------

    @Override
    public void sendNotification(NotificationRequest request) {
        String notificationId = UUID.randomUUID().toString();
        NotificationRecord record = createPendingRecord(notificationId, request);
        notificationRepository.save(record);

        boolean sent = sendWithExponentialBackoff(request, record);

        record.setUpdatedAt(LocalDateTime.now());
        if (sent) {
            record.setStatus(NotificationStatus.Status.SENT);
        } else {
            record.setStatus(NotificationStatus.Status.FAILED);
        }
        notificationRepository.save(record);
    }

    @Override
    public NotificationStatus getStatus(String notificationId) {
        return notificationRepository.findById(notificationId)
                .map(r -> {
                    NotificationStatus ns = new NotificationStatus();
                    ns.setNotificationId(r.getNotificationId());
                    ns.setStatus(r.getStatus());
                    ns.setChannel(r.getChannel());
                    ns.setSentAt(r.getUpdatedAt());
                    ns.setErrorMessage(r.getErrorMessage());
                    return ns;
                })
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts to send a notification with exponential backoff retry.
     * Returns true if sent successfully, false after exhausting all attempts.
     */
    private boolean sendWithExponentialBackoff(NotificationRequest request, NotificationRecord record) {
        long intervalMs = initialIntervalMs;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                record.setAttemptCount(attempt);
                dispatchToChannel(request);
                return true;
            } catch (Exception e) {
                lastException = e;
                log.warn("Notification attempt {}/{} failed for notificationId={} channel={}: {}",
                        attempt, maxAttempts, record.getNotificationId(), request.channel(), e.getMessage());

                if (attempt < maxAttempts) {
                    sleep(intervalMs);
                    intervalMs = Math.min((long) (intervalMs * multiplier), maxIntervalMs);
                }
            }
        }

        String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
        record.setErrorMessage(errorMsg);
        log.error("All {} attempts exhausted for notificationId={} channel={}. Marking FAILED.",
                maxAttempts, record.getNotificationId(), request.channel());
        return false;
    }

    /**
     * Routes the notification to the correct channel (EMAIL, SMS, PUSH).
     */
    private void dispatchToChannel(NotificationRequest request) {
        switch (request.channel().toUpperCase()) {
            case CHANNEL_EMAIL -> emailChannel.send(request.recipientId(), request.subject(), request.body());
            case CHANNEL_SMS   -> smsAndPushChannel.sendSms(request.recipientId(), request.body());
            case CHANNEL_PUSH  -> smsAndPushChannel.sendPush(request.recipientId(), request.body());
            default -> throw new IllegalArgumentException("Unknown notification channel: " + request.channel());
        }
    }

    private NotificationRecord createPendingRecord(String notificationId, NotificationRequest request) {
        NotificationRecord record = new NotificationRecord();
        record.setNotificationId(notificationId);
        record.setRecipientId(request.recipientId());
        record.setChannel(request.channel());
        record.setSubject(request.subject());
        record.setBody(request.body());
        record.setCorrelationId(request.correlationId());
        record.setStatus(NotificationStatus.Status.PENDING);
        record.setAttemptCount(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private NotificationRequest buildRequestFromTransactionEvent(Map<String, Object> payload) {
        String accountId = String.valueOf(payload.getOrDefault("sourceAccountId", "unknown"));
        String amount    = String.valueOf(payload.getOrDefault("amount", "0"));
        String corrId    = String.valueOf(payload.getOrDefault("correlationId", ""));
        return new NotificationRequest(
                accountId,
                CHANNEL_EMAIL,
                "Transaction Completed",
                "Your transaction of " + amount + " has been processed successfully.",
                corrId
        );
    }

    private NotificationRequest buildRequestFromAccountEvent(Map<String, Object> payload) {
        String customerId = String.valueOf(payload.getOrDefault("customerId", "unknown"));
        String action     = String.valueOf(payload.getOrDefault("action", "updated"));
        String corrId     = String.valueOf(payload.getOrDefault("correlationId", ""));
        return new NotificationRequest(
                customerId,
                CHANNEL_EMAIL,
                "Account " + action,
                "Your account has been " + action.toLowerCase() + ".",
                corrId
        );
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
