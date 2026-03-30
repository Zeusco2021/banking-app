package com.bank.audit.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document for immutable audit events.
 *
 * The collection is configured append-only: no update or delete operations
 * are permitted (enforced via MongoCollectionConfig at startup).
 *
 * Indexes support the required query patterns (Req 6.4):
 *   - correlationId, actorId, resourceId, timestamp range
 */
@Document(collection = "audit_events")
@CompoundIndexes({
    @CompoundIndex(name = "idx_actor_ts",    def = "{'actorId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_resource_ts", def = "{'resourceId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_corr_ts",     def = "{'correlationId': 1, 'timestamp': -1}")
})
public class AuditEventDocument {

    @Id
    private String eventId;          // UUID — unique, immutable PK

    @Indexed
    private String correlationId;

    private String serviceOrigin;
    private String action;

    @Indexed
    private String actorId;

    @Indexed
    private String resourceId;

    private Object payload;          // Flexible JSON snapshot
    private String ipAddress;

    @Indexed
    private LocalDateTime timestamp;

    // Always true — documents are never modified after insert
    private boolean immutable = true;

    public AuditEventDocument() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getServiceOrigin() { return serviceOrigin; }
    public void setServiceOrigin(String serviceOrigin) { this.serviceOrigin = serviceOrigin; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isImmutable() { return immutable; }
    public void setImmutable(boolean immutable) { this.immutable = immutable; }
}
