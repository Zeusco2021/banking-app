package com.bank.gateway.canary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monitors the error rate of the new (canary) service every 30 seconds.
 * If the error rate exceeds the configured threshold (default 1 %), rollback is
 * automatically activated, routing 100 % of traffic back to the Legacy Adapter.
 *
 * <p>Requisitos: 7.7, 1.6
 */
@Component
public class CanaryMonitor {

    private static final Logger log = LoggerFactory.getLogger(CanaryMonitor.class);

    /** Error rate threshold above which rollback is triggered (default 1 % = 0.01). */
    @Value("${canary.error-rate-threshold:0.01}")
    private double errorRateThreshold;

    private final CanaryRoutingState routingState;

    public CanaryMonitor(CanaryRoutingState routingState) {
        this.routingState = routingState;
    }

    /**
     * Runs every 30 seconds. Reads the error rate snapshot from CanaryRoutingState,
     * resets the counters, and triggers rollback if the threshold is exceeded.
     *
     * Requisito 7.7: "WHEN el error rate del nuevo servicio supera el 1% durante un
     * Canary Deployment, THE Sistema SHALL activar rollback automático enrutando el
     * 100% del tráfico al Legacy_Adapter."
     */
    @Scheduled(fixedDelayString = "${canary.monitor-interval-ms:30000}")
    public void checkErrorRate() {
        // Only monitor when canary traffic is actually being sent
        if (routingState.effectiveTrafficPercentage() == 0 && !routingState.isRollbackActive()) {
            return;
        }

        double errorRate = routingState.snapshotAndResetErrorRate();
        log.debug("Canary error rate snapshot: {:.2f}%", errorRate * 100);

        if (errorRate > errorRateThreshold) {
            log.warn("Canary error rate {:.2f}% exceeds threshold {:.2f}% — triggering rollback",
                    errorRate * 100, errorRateThreshold * 100);
            routingState.activateRollback();
        } else if (routingState.isRollbackActive()) {
            // Log that rollback remains active; operator must deactivate manually
            log.info("Rollback is active. Current error rate {:.2f}% is within threshold. "
                    + "Operator must deactivate rollback manually.", errorRate * 100);
        }
    }
}
