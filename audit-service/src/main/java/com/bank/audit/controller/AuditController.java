package com.bank.audit.controller;

import com.bank.audit.service.AuditServiceImpl;
import com.bank.shared.model.AuditEvent;
import com.bank.shared.service.AuditService.AuditQuery;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller exposing audit event query endpoints.
 * All endpoints are read-only — no write operations are exposed via HTTP.
 */
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final AuditServiceImpl auditService;

    public AuditController(AuditServiceImpl auditService) {
        this.auditService = auditService;
    }

    /**
     * Query audit events with optional filters.
     *
     * GET /v1/audit/events?correlationId=...&actorId=...&resourceId=...&from=...&to=...
     */
    @GetMapping("/events")
    public ResponseEntity<List<AuditEvent>> queryEvents(
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        AuditQuery query = new AuditQuery(correlationId, actorId, resourceId, from, to);
        List<AuditEvent> events = auditService.queryEvents(query);
        return ResponseEntity.ok(events);
    }
}
