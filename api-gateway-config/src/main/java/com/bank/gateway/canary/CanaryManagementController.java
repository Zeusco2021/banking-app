package com.bank.gateway.canary;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal management API for controlling canary traffic weights and rollback state.
 * Should be secured behind an internal network policy / IAM in production.
 *
 * Requisitos: 1.6, 7.6, 7.7
 */
@RestController
@RequestMapping("/internal/canary")
public class CanaryManagementController {

    private final CanaryRoutingState routingState;

    public CanaryManagementController(CanaryRoutingState routingState) {
        this.routingState = routingState;
    }

    /** Returns the current canary routing state. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "trafficPercentage", routingState.getTrafficPercentage(),
                "effectiveTrafficPercentage", routingState.effectiveTrafficPercentage(),
                "rollbackActive", routingState.isRollbackActive()
        ));
    }

    /**
     * Updates the percentage of traffic routed to the new service (0–100).
     * Requisito 1.6 — configurable canary traffic weight.
     */
    @PutMapping("/traffic")
    public ResponseEntity<Map<String, Object>> setTrafficPercentage(
            @RequestParam int percentage) {
        routingState.setTrafficPercentage(percentage);
        return ResponseEntity.ok(Map.of(
                "trafficPercentage", routingState.getTrafficPercentage(),
                "effectiveTrafficPercentage", routingState.effectiveTrafficPercentage()
        ));
    }

    /**
     * Manually activates rollback (routes 100 % to Legacy Adapter).
     * Requisito 7.7
     */
    @PutMapping("/rollback/activate")
    public ResponseEntity<Map<String, Object>> activateRollback() {
        routingState.activateRollback();
        return ResponseEntity.ok(Map.of("rollbackActive", true));
    }

    /**
     * Deactivates rollback after the new service has been stabilised.
     * Requisito 7.7
     */
    @DeleteMapping("/rollback")
    public ResponseEntity<Map<String, Object>> deactivateRollback() {
        routingState.deactivateRollback();
        return ResponseEntity.ok(Map.of(
                "rollbackActive", false,
                "effectiveTrafficPercentage", routingState.effectiveTrafficPercentage()
        ));
    }
}
