package com.bank.audit.service;

import com.bank.audit.model.AuditEventDocument;
import com.bank.shared.service.AuditService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test verifying that AuditService correctly consumes events from:
 * - transactions.completed (Req 4.4, 6.1)
 * - audit.events (Req 2.8, 6.1, 12.8)
 *
 * Uses real Kafka (Testcontainers) and real MongoDB (Testcontainers).
 */
@SpringBootTest
@Testcontainers
class AuditKafkaConsumerTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    MongoTemplate mongoTemplate;

    // -------------------------------------------------------------------------
    // Req 4.4, 6.1 — AuditService consumes transactions.completed
    // -------------------------------------------------------------------------

    @Test
    void auditServiceConsumesTransactionCompletedEvent() {
        String txnId = UUID.randomUUID().toString();
        String corrId = UUID.randomUUID().toString();

        Map<String, Object> event = new HashMap<>();
        event.put("transactionId", txnId);
        event.put("sourceAccountId", "acc-src-001");
        event.put("targetAccountId", "acc-dst-002");
        event.put("amount", "500.00");
        event.put("currency", "USD");
        event.put("correlationId", corrId);
        event.put("timestamp", java.time.LocalDateTime.now().toString());

        publishToKafka("transactions.completed", txnId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<AuditEventDocument> docs = mongoTemplate.find(
                    Query.query(Criteria.where("resourceId").is(txnId)),
                    AuditEventDocument.class, "audit_events");
            assertThat(docs).isNotEmpty();
            AuditEventDocument doc = docs.get(0);
            assertThat(doc.getAction()).isEqualTo("PROCESS_TRANSACTION");
            assertThat(doc.getServiceOrigin()).isEqualTo("transaction-service");
            assertThat(doc.getCorrelationId()).isEqualTo(corrId);
            assertThat(doc.isImmutable()).isTrue();
        });
    }

    // -------------------------------------------------------------------------
    // Req 2.8, 12.8 — AuditService consumes LOGIN_SUCCESS / LOGIN_FAILED
    // -------------------------------------------------------------------------

    @Test
    void auditServiceConsumesLoginSuccessEvent() {
        String eventId = UUID.randomUUID().toString();
        String corrId  = UUID.randomUUID().toString();
        String actor   = "user-" + UUID.randomUUID();

        Map<String, Object> event = buildAuditEventPayload(eventId, corrId, "auth-service",
                "LOGIN_SUCCESS", actor, actor, "192.168.1.1", null);

        publishToKafka("audit.events", eventId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            AuditEventDocument doc = mongoTemplate.findById(eventId, AuditEventDocument.class, "audit_events");
            assertThat(doc).isNotNull();
            assertThat(doc.getAction()).isEqualTo("LOGIN_SUCCESS");
            assertThat(doc.getActorId()).isEqualTo(actor);
            assertThat(doc.getServiceOrigin()).isEqualTo("auth-service");
        });
    }

    @Test
    void auditServiceConsumesLoginFailedEvent() {
        String eventId = UUID.randomUUID().toString();
        String corrId  = UUID.randomUUID().toString();
        String actor   = "user-" + UUID.randomUUID();

        Map<String, Object> event = buildAuditEventPayload(eventId, corrId, "auth-service",
                "LOGIN_FAILED", actor, actor, "10.0.0.5", "Invalid credentials");

        publishToKafka("audit.events", eventId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            AuditEventDocument doc = mongoTemplate.findById(eventId, AuditEventDocument.class, "audit_events");
            assertThat(doc).isNotNull();
            assertThat(doc.getAction()).isEqualTo("LOGIN_FAILED");
            assertThat(doc.getActorId()).isEqualTo(actor);
        });
    }

    // -------------------------------------------------------------------------
    // Req 12.8 — sensitive data access is audited
    // -------------------------------------------------------------------------

    @Test
    void auditServiceConsumesSensitiveDataAccessEvent() {
        String eventId = UUID.randomUUID().toString();
        String corrId  = UUID.randomUUID().toString();
        String actor   = "user-" + UUID.randomUUID();
        String resource = "customer-profile-" + UUID.randomUUID();

        Map<String, Object> event = buildAuditEventPayload(eventId, corrId, "account-service",
                "SENSITIVE_DATA_ACCESS", actor, resource, "172.16.0.1", null);

        publishToKafka("audit.events", eventId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            AuditEventDocument doc = mongoTemplate.findById(eventId, AuditEventDocument.class, "audit_events");
            assertThat(doc).isNotNull();
            assertThat(doc.getAction()).isEqualTo("SENSITIVE_DATA_ACCESS");
            assertThat(doc.getResourceId()).isEqualTo(resource);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void publishToKafka(String topic, String key, Object value) {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        KafkaTemplate<String, Object> producer = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps));
        producer.send(new ProducerRecord<>(topic, key, value));
        producer.flush();
    }

    private Map<String, Object> buildAuditEventPayload(String eventId, String corrId,
                                                        String serviceOrigin, String action,
                                                        String actorId, String resourceId,
                                                        String ipAddress, Object payload) {
        Map<String, Object> m = new HashMap<>();
        m.put("eventId", eventId);
        m.put("correlationId", corrId);
        m.put("serviceOrigin", serviceOrigin);
        m.put("action", action);
        m.put("actorId", actorId);
        m.put("resourceId", resourceId);
        m.put("ipAddress", ipAddress);
        m.put("payload", payload);
        m.put("timestamp", java.time.LocalDateTime.now().toString());
        return m;
    }
}
