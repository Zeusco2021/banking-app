package com.bank.transaction.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a pending Kafka event stored in the OUTBOX table.
 * Used when Kafka is unavailable — the OutboxProcessor retries publishing.
 * Satisfies Requirements 4.5, 10.6.
 */
@Entity
@Table(name = "OUTBOX")
public class OutboxEvent {

    public enum OutboxStatus {
        PENDING, PUBLISHED, FAILED
    }

    @Id
    @Column(name = "EVENT_ID", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "TOPIC", nullable = false)
    private String topic;

    @Column(name = "PAYLOAD", nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private OutboxStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @Column(name = "RETRY_COUNT")
    private int retryCount;

    public OutboxEvent() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
