package com.bank.gateway.canary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the mutable canary routing state shared between the CanaryFilter and CanaryMonitor.
 *
 * <p>Traffic split is expressed as an integer percentage (0–100) of requests that should
 * be routed to the new service. When rollback is active the percentage is forced to 0
 * and all traffic goes to the Legacy Adapter — Requisitos 1.6, 7.4, 7.5, 7.6, 7.7.
 */
@Component
@ConfigurationProperties(prefix = "canary")
public class CanaryRoutingState {

    private static final Logger log = LoggerFactory.getLogger(CanaryRoutingState.class);

    /** Percentage of traffic (0–100) to route to the new service. Configurable at runtime. */
    private volatile int trafficPercentage = 0;

    /** When true, 100 % of traffic is forced to the Legacy Adapter regardless of trafficPercentage. */
    private final AtomicBoolean rollbackActive = new AtomicBoolean(false);

    /** Rolling error counter used by CanaryMonitor (reset on each monitoring window). */
    private final AtomicInteger newServiceErrors = new AtomicInteger(0);

    /** Total request counter for the new service in the current monitoring window. */
    private final AtomicInteger newServiceRequests = new AtomicInteger(0);

    // -------------------------------------------------------------------------
    // Traffic routing
    // -------------------------------------------------------------------------

    /**
     * Returns the effective traffic percentage for the new service.
     * Always 0 when rollback is active.
     */
    public int effectiveTrafficPercentage() {
        return rollbackActive.get() ? 0 : trafficPercentage;
    }

    public int getTrafficPercentage() {
        return trafficPercentage;
    }

    public void setTrafficPercentage(int trafficPercentage) {
        this.trafficPercentage = Math.max(0, Math.min(100, trafficPercentage));
    }

    // -------------------------------------------------------------------------
    // Rollback
    // -------------------------------------------------------------------------

    public boolean isRollbackActive() {
        return rollbackActive.get();
    }

    /**
     * Activates rollback: forces all traffic to the Legacy Adapter.
     * Requisito 7.7
     */
    public void activateRollback() {
        if (rollbackActive.compareAndSet(false, true)) {
            log.warn("Canary rollback ACTIVATED — routing 100% of traffic to Legacy Adapter");
        }
    }

    /**
     * Deactivates rollback, restoring the configured traffic split.
     * Should only be called by an operator after the new service is healthy again.
     */
    public void deactivateRollback() {
        if (rollbackActive.compareAndSet(true, false)) {
            log.info("Canary rollback DEACTIVATED — restoring {}% traffic to new service",
                    trafficPercentage);
        }
    }

    // -------------------------------------------------------------------------
    // Error rate tracking (used by CanaryMonitor)
    // -------------------------------------------------------------------------

    public void recordNewServiceRequest() {
        newServiceRequests.incrementAndGet();
    }

    public void recordNewServiceError() {
        newServiceErrors.incrementAndGet();
    }

    /**
     * Reads and resets the error rate counters atomically.
     *
     * @return error rate as a value in [0.0, 1.0], or 0.0 if no requests were made
     */
    public double snapshotAndResetErrorRate() {
        int errors = newServiceErrors.getAndSet(0);
        int requests = newServiceRequests.getAndSet(0);
        if (requests == 0) {
            return 0.0;
        }
        return (double) errors / requests;
    }
}
