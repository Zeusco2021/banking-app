package com.bank.auth.service;

import com.bank.shared.model.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

    public void publish(String action, String actorId, String resourceId, String ipAddress, Object payload) {
        AuditEvent event = new AuditEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setServiceOrigin("auth-service");
        event.setAction(action);
        event.setActorId(actorId);
        event.setResourceId(resourceId);
        event.setIpAddress(ipAddress);
        event.setPayload(payload);
        event.setTimestamp(LocalDateTime.now());
        event.setImmutable(true);

        // Kafka stub — log for now; real Kafka producer wired in a later task
        log.info("[AUDIT] eventId={} action={} actor={} resource={} ip={}",
            event.getEventId(), event.getAction(), event.getActorId(),
            event.getResourceId(), event.getIpAddress());
    }
}
