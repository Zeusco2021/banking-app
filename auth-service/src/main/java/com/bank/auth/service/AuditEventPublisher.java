package com.bank.auth.service;

import com.bank.shared.model.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes audit events to the Kafka topic {@code audit.events}.
 *
 * The AuditService consumes this topic and persists events to MongoDB.
 * Satisfies Req 2.8 (LOGIN_SUCCESS / LOGIN_FAILED) and Req 12.8 (sensitive data access).
 */
@Service
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);
    private static final String TOPIC_AUDIT_EVENTS = "audit.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuditEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Builds and publishes an AuditEvent to the {@code audit.events} Kafka topic.
     *
     * @param action     e.g. LOGIN_SUCCESS, LOGIN_FAILED, SENSITIVE_DATA_ACCESS
     * @param actorId    username or userId performing the action
     * @param resourceId resource being accessed (e.g. username, accountId)
     * @param ipAddress  client IP address (null falls back to "unknown")
     * @param payload    optional additional context
     */
    public void publish(String action, String actorId, String resourceId,
                        String ipAddress, Object payload) {
        String eventId = UUID.randomUUID().toString();
        String resolvedIp = ipAddress != null ? ipAddress : "unknown";

        // Build a flat map so the AuditService can deserialize it via mapToAuditEvent()
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", eventId);
        event.put("correlationId", resolvedCorrelationId());
        event.put("serviceOrigin", "auth-service");
        event.put("action", action);
        event.put("actorId", actorId != null ? actorId : "unknown");
        event.put("resourceId", resourceId != null ? resourceId : "unknown");
        event.put("ipAddress", resolvedIp);
        event.put("payload", payload);
        event.put("timestamp", LocalDateTime.now().toString());

        try {
            kafkaTemplate.send(TOPIC_AUDIT_EVENTS, eventId, event);
            log.debug("[AUDIT] Published eventId={} action={} actor={} resource={} ip={}",
                    eventId, action, actorId, resourceId, resolvedIp);
        } catch (Exception e) {
            // Audit publishing must never break the auth flow — log and continue
            log.error("[AUDIT] Failed to publish audit event eventId={} action={}: {}",
                    eventId, action, e.getMessage(), e);
        }
    }

    /**
     * Attempts to retrieve the correlationId from the MDC (set by CorrelationIdFilter).
     * Falls back to a new UUID if not present.
     */
    private String resolvedCorrelationId() {
        String corrId = org.slf4j.MDC.get("correlationId");
        return corrId != null && !corrId.isBlank() ? corrId : UUID.randomUUID().toString();
    }
}
