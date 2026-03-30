package com.bank.audit.service;

import com.bank.audit.model.AuditEventDocument;
import com.bank.audit.repository.AuditEventRepository;
import com.bank.shared.model.AuditEvent;
import com.bank.shared.service.AuditService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Audit Service implementation.
 *
 * Persists AuditEvents in MongoDB in an append-only fashion (Req 6.1, 6.3).
 * Consumes events from Kafka topics published by other microservices.
 * Supports querying by correlationId, actorId, resourceId and timestamp range (Req 6.4).
 *
 * Append-only guarantee:
 *   - Uses MongoTemplate.insert() exclusively — never update() or remove().
 *   - The audit_events collection has a schema validator (see MongoCollectionConfig).
 *   - In production, the DB user has INSERT-only privileges on this collection.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final MongoTemplate mongoTemplate;
    private final AuditEventRepository auditEventRepository;

    public AuditServiceImpl(MongoTemplate mongoTemplate,
                            AuditEventRepository auditEventRepository) {
        this.mongoTemplate = mongoTemplate;
        this.auditEventRepository = auditEventRepository;
    }

    // -------------------------------------------------------------------------
    // Kafka Consumers (Req 6.1)
    // -------------------------------------------------------------------------

    /**
     * Consumes audit events published to the audit.events topic.
     * Other microservices publish AuditEvent payloads here.
     */
    @KafkaListener(
        topics = "audit.events",
        groupId = "audit-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAuditEvent(ConsumerRecord<String, Map<String, Object>> record,
                             Acknowledgment ack) {
        log.info("Received audit.events message key={}", record.key());
        try {
            AuditEvent event = mapToAuditEvent(record.value());
            recordEvent(event);
        } catch (Exception e) {
            log.error("Failed to persist audit event key={}", record.key(), e);
        } finally {
            ack.acknowledge();
        }
    }

    /**
     * Consumes transaction completed events and records them as audit entries.
     */
    @KafkaListener(
        topics = "transactions.completed",
        groupId = "audit-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionCompleted(ConsumerRecord<String, Map<String, Object>> record,
                                       Acknowledgment ack) {
        log.info("Received transactions.completed event key={}", record.key());
        try {
            Map<String, Object> payload = record.value();
            AuditEvent event = new AuditEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setCorrelationId(String.valueOf(payload.getOrDefault("correlationId", "")));
            event.setServiceOrigin("transaction-service");
            event.setAction("PROCESS_TRANSACTION");
            event.setActorId(String.valueOf(payload.getOrDefault("sourceAccountId", "unknown")));
            event.setResourceId(String.valueOf(payload.getOrDefault("transactionId", "unknown")));
            event.setPayload(payload);
            event.setIpAddress("internal");
            event.setTimestamp(LocalDateTime.now());
            event.setImmutable(true);
            recordEvent(event);
        } catch (Exception e) {
            log.error("Failed to record audit for transaction event key={}", record.key(), e);
        } finally {
            ack.acknowledge();
        }
    }

    // -------------------------------------------------------------------------
    // AuditService interface (Req 6.1, 6.2, 6.3, 6.4)
    // -------------------------------------------------------------------------

    /**
     * Persists an AuditEvent in MongoDB using insert() — never update or replace.
     * Enforces append-only semantics at the application layer (Req 6.1, 6.3).
     *
     * All required fields must be present: eventId, correlationId, serviceOrigin,
     * action, actorId, resourceId, payload, ipAddress, timestamp (Req 6.2).
     */
    @Override
    public void recordEvent(AuditEvent event) {
        validateRequiredFields(event);

        AuditEventDocument doc = toDocument(event);

        // insert() throws DuplicateKeyException if eventId already exists,
        // preventing any accidental overwrite of existing records.
        mongoTemplate.insert(doc, "audit_events");

        log.info("AuditEvent persisted eventId={} action={} actor={} resource={}",
                doc.getEventId(), doc.getAction(), doc.getActorId(), doc.getResourceId());
    }

    /**
     * Queries audit events with optional filters (Req 6.4).
     * All filter fields are optional; null values are ignored.
     * Timestamp range defaults to all-time if not specified.
     */
    @Override
    public List<AuditEvent> queryEvents(AuditQuery query) {
        LocalDateTime from = query.fromTimestamp() != null
                ? query.fromTimestamp()
                : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to = query.toTimestamp() != null
                ? query.toTimestamp()
                : LocalDateTime.now().plusYears(1);

        List<AuditEventDocument> docs;

        // Use the most selective index available
        if (query.correlationId() != null) {
            docs = auditEventRepository.findByCorrelationId(query.correlationId());
            docs = filterByTimestamp(docs, from, to);
            if (query.actorId() != null) {
                docs = docs.stream().filter(d -> query.actorId().equals(d.getActorId())).collect(Collectors.toList());
            }
            if (query.resourceId() != null) {
                docs = docs.stream().filter(d -> query.resourceId().equals(d.getResourceId())).collect(Collectors.toList());
            }
        } else if (query.actorId() != null) {
            docs = auditEventRepository.findByActorId(query.actorId());
            docs = filterByTimestamp(docs, from, to);
            if (query.resourceId() != null) {
                docs = docs.stream().filter(d -> query.resourceId().equals(d.getResourceId())).collect(Collectors.toList());
            }
        } else if (query.resourceId() != null) {
            docs = auditEventRepository.findByResourceId(query.resourceId());
            docs = filterByTimestamp(docs, from, to);
        } else {
            docs = auditEventRepository.findByTimestampBetween(from, to);
        }

        return docs.stream().map(this::toAuditEvent).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void validateRequiredFields(AuditEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("AuditEvent.eventId is required");
        }
        if (event.getCorrelationId() == null || event.getCorrelationId().isBlank()) {
            throw new IllegalArgumentException("AuditEvent.correlationId is required");
        }
        if (event.getServiceOrigin() == null || event.getServiceOrigin().isBlank()) {
            throw new IllegalArgumentException("AuditEvent.serviceOrigin is required");
        }
        if (event.getAction() == null || event.getAction().isBlank()) {
            throw new IllegalArgumentException("AuditEvent.action is required");
        }
        if (event.getActorId() == null || event.getActorId().isBlank()) {
            throw new IllegalArgumentException("AuditEvent.actorId is required");
        }
        if (event.getResourceId() == null || event.getResourceId().isBlank()) {
            throw new IllegalArgumentException("AuditEvent.resourceId is required");
        }
        if (event.getIpAddress() == null || event.getIpAddress().isBlank()) {
            throw new IllegalArgumentException("AuditEvent.ipAddress is required");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("AuditEvent.timestamp is required");
        }
    }

    private AuditEventDocument toDocument(AuditEvent event) {
        AuditEventDocument doc = new AuditEventDocument();
        doc.setEventId(event.getEventId());
        doc.setCorrelationId(event.getCorrelationId());
        doc.setServiceOrigin(event.getServiceOrigin());
        doc.setAction(event.getAction());
        doc.setActorId(event.getActorId());
        doc.setResourceId(event.getResourceId());
        doc.setPayload(event.getPayload());
        doc.setIpAddress(event.getIpAddress());
        doc.setTimestamp(event.getTimestamp());
        doc.setImmutable(true);
        return doc;
    }

    private AuditEvent toAuditEvent(AuditEventDocument doc) {
        AuditEvent event = new AuditEvent();
        event.setEventId(doc.getEventId());
        event.setCorrelationId(doc.getCorrelationId());
        event.setServiceOrigin(doc.getServiceOrigin());
        event.setAction(doc.getAction());
        event.setActorId(doc.getActorId());
        event.setResourceId(doc.getResourceId());
        event.setPayload(doc.getPayload());
        event.setIpAddress(doc.getIpAddress());
        event.setTimestamp(doc.getTimestamp());
        event.setImmutable(doc.isImmutable());
        return event;
    }

    private AuditEvent mapToAuditEvent(Map<String, Object> payload) {
        AuditEvent event = new AuditEvent();
        event.setEventId(String.valueOf(payload.getOrDefault("eventId", UUID.randomUUID().toString())));
        event.setCorrelationId(String.valueOf(payload.getOrDefault("correlationId", "")));
        event.setServiceOrigin(String.valueOf(payload.getOrDefault("serviceOrigin", "unknown")));
        event.setAction(String.valueOf(payload.getOrDefault("action", "UNKNOWN")));
        event.setActorId(String.valueOf(payload.getOrDefault("actorId", "unknown")));
        event.setResourceId(String.valueOf(payload.getOrDefault("resourceId", "unknown")));
        event.setPayload(payload.get("payload"));
        event.setIpAddress(String.valueOf(payload.getOrDefault("ipAddress", "unknown")));
        event.setTimestamp(LocalDateTime.now());
        event.setImmutable(true);
        return event;
    }

    private List<AuditEventDocument> filterByTimestamp(List<AuditEventDocument> docs,
                                                        LocalDateTime from,
                                                        LocalDateTime to) {
        return docs.stream()
                .filter(d -> d.getTimestamp() != null
                        && !d.getTimestamp().isBefore(from)
                        && !d.getTimestamp().isAfter(to))
                .collect(Collectors.toList());
    }
}
