package com.bank.shared.service;

import com.bank.shared.model.AuditEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditService {

    record AuditQuery(
        String correlationId,
        String actorId,
        String resourceId,
        LocalDateTime fromTimestamp,
        LocalDateTime toTimestamp
    ) {}

    void recordEvent(AuditEvent event);

    List<AuditEvent> queryEvents(AuditQuery query);
}
