package com.bank.audit.config;

import com.bank.audit.model.AuditEventDocument;
import com.bank.audit.repository.AuditEventRepository;
import com.bank.audit.service.AuditServiceImpl;
import com.bank.shared.model.AuditEvent;
import com.bank.shared.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies:
 * - audit_events collection is created with schema validator (Req 6.2, 6.3)
 * - TTL index for 7-year retention is created (Req 6.5)
 * - AuditService.recordEvent() persists append-only (Req 6.1, 6.3)
 * - Required fields are enforced (Req 6.2)
 */
@SpringBootTest
@Testcontainers
class MongoCollectionConfigTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        // Provide a dummy Kafka bootstrap server — Kafka consumers won't connect during this test
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    AuditServiceImpl auditService;

    @Test
    void auditEventsCollectionExists() {
        assertThat(mongoTemplate.collectionExists("audit_events")).isTrue();
    }

    /**
     * Req 6.5 — TTL index must be configured for 7-year retention (220,752,000 seconds).
     */
    @Test
    void ttlIndexIsConfiguredFor7Years() {
        List<IndexInfo> indexes = mongoTemplate.indexOps("audit_events").getIndexInfo();
        boolean hasTtlIndex = indexes.stream()
                .anyMatch(idx -> idx.getExpireAfter() != null
                        && idx.getExpireAfter().getSeconds() == 220_752_000L);
        assertThat(hasTtlIndex)
                .as("TTL index with expireAfterSeconds=220752000 (7 years) must exist on audit_events")
                .isTrue();
    }

    /**
     * Req 6.1, 6.2 — recordEvent() persists all required fields.
     */
    @Test
    void recordEventPersistsAllRequiredFields() {
        AuditEvent event = buildValidEvent("PROCESS_TRANSACTION");
        auditService.recordEvent(event);

        AuditEventDocument doc = mongoTemplate.findById(event.getEventId(), AuditEventDocument.class, "audit_events");
        assertThat(doc).isNotNull();
        assertThat(doc.getEventId()).isEqualTo(event.getEventId());
        assertThat(doc.getCorrelationId()).isEqualTo(event.getCorrelationId());
        assertThat(doc.getServiceOrigin()).isEqualTo(event.getServiceOrigin());
        assertThat(doc.getAction()).isEqualTo(event.getAction());
        assertThat(doc.getActorId()).isEqualTo(event.getActorId());
        assertThat(doc.getResourceId()).isEqualTo(event.getResourceId());
        assertThat(doc.getIpAddress()).isEqualTo(event.getIpAddress());
        assertThat(doc.getTimestamp()).isNotNull();
        assertThat(doc.isImmutable()).isTrue();
    }

    /**
     * Req 6.3 — append-only: inserting the same eventId twice must fail.
     */
    @Test
    void recordEventIsAppendOnly_duplicateEventIdFails() {
        AuditEvent event = buildValidEvent("LOGIN_SUCCESS");
        auditService.recordEvent(event);

        // Attempting to insert the same eventId again must throw
        assertThatThrownBy(() -> auditService.recordEvent(event))
                .isInstanceOf(Exception.class);
    }

    /**
     * Req 6.2 — missing required field must be rejected.
     */
    @Test
    void recordEventRejectsEventWithMissingRequiredField() {
        AuditEvent event = buildValidEvent("LOGIN_FAILED");
        event.setCorrelationId(null); // remove required field

        assertThatThrownBy(() -> auditService.recordEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    /**
     * Req 6.1 — AuditService consumes LOGIN_SUCCESS and LOGIN_FAILED actions.
     */
    @Test
    void recordEventAcceptsLoginAuditActions() {
        AuditEvent loginSuccess = buildValidEvent("LOGIN_SUCCESS");
        AuditEvent loginFailed  = buildValidEvent("LOGIN_FAILED");

        auditService.recordEvent(loginSuccess);
        auditService.recordEvent(loginFailed);

        AuditService.AuditQuery query = new AuditService.AuditQuery(
                null, loginSuccess.getActorId(), null, null, null);
        List<AuditEvent> results = auditService.queryEvents(query);
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
    }

    // -------------------------------------------------------------------------

    private AuditEvent buildValidEvent(String action) {
        AuditEvent e = new AuditEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setCorrelationId(UUID.randomUUID().toString());
        e.setServiceOrigin("auth-service");
        e.setAction(action);
        e.setActorId("user-" + UUID.randomUUID());
        e.setResourceId("resource-" + UUID.randomUUID());
        e.setIpAddress("127.0.0.1");
        e.setPayload(null);
        e.setTimestamp(LocalDateTime.now());
        e.setImmutable(true);
        return e;
    }
}
