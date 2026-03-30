package com.bank.shared.model;

import java.time.LocalDateTime;

public class AuditEvent {

    private String eventId;
    private String correlationId;
    private String serviceOrigin;
    private String action;
    private String actorId;
    private String resourceId;
    private Object payload;
    private String ipAddress;
    private LocalDateTime timestamp;
    private boolean immutable = true;

    public AuditEvent() {}

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
