package com.bank.gateway.filter;

import com.bank.gateway.canary.CanaryRoutingState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Routes a configurable percentage of traffic to the new microservice (canary) and
 * the remainder to the Legacy Adapter, implementing the Strangler Fig canary strategy.
 *
 * <p>Mutual exclusion guarantee (Requisito 7.4): each request is tagged with a
 * {@code X-Route-Target} header set to either {@code "new"} or {@code "legacy"}.
 * Downstream components (API Gateway, load balancer) use this header to select the
 * correct backend. A request is NEVER sent to both systems simultaneously.
 *
 * <p>When rollback is active (Requisito 7.7), all requests are tagged as {@code "legacy"}.
 *
 * <p>Requisitos: 1.6, 7.4, 7.5, 7.6, 7.7
 */
@Component
@Order(2)   // Runs after RateLimitFilter (Order 1)
public class CanaryRoutingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CanaryRoutingFilter.class);

    /** Header set on every request to indicate which backend should handle it. */
    public static final String ROUTE_TARGET_HEADER = "X-Route-Target";
    public static final String TARGET_NEW          = "new";
    public static final String TARGET_LEGACY       = "legacy";

    /** Header used to record which target was chosen (for observability). */
    public static final String ROUTE_DECISION_HEADER = "X-Canary-Decision";

    @Value("${canary.new-service-url:http://localhost:8090}")
    private String newServiceBaseUrl;

    @Value("${canary.legacy-adapter-url:http://localhost:8086}")
    private String legacyAdapterBaseUrl;

    private final CanaryRoutingState routingState;

    public CanaryRoutingFilter(CanaryRoutingState routingState) {
        this.routingState = routingState;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String target = selectTarget();

        // Tag the request so downstream routing infrastructure knows where to send it.
        // We use a mutable wrapper to add the header since HttpServletRequest is immutable.
        CanaryRequestWrapper wrappedRequest = new CanaryRequestWrapper(request, target);

        // Record the routing decision in the response for observability
        response.setHeader(ROUTE_DECISION_HEADER, target);

        log.debug("Canary routing: path={} target={} rollback={}",
                request.getRequestURI(), target, routingState.isRollbackActive());

        // Track request count for error-rate monitoring
        if (TARGET_NEW.equals(target)) {
            routingState.recordNewServiceRequest();
        }

        try {
            filterChain.doFilter(wrappedRequest, response);

            // Record errors from the new service (5xx responses)
            if (TARGET_NEW.equals(target) && response.getStatus() >= 500) {
                routingState.recordNewServiceError();
            }
        } catch (Exception e) {
            if (TARGET_NEW.equals(target)) {
                routingState.recordNewServiceError();
            }
            throw e;
        }
    }

    /**
     * Selects the routing target based on the current traffic percentage.
     * Uses a random number to achieve the configured split probabilistically.
     *
     * Mutual exclusion: returns exactly one of "new" or "legacy" — never both.
     * Requisito 7.4
     */
    private String selectTarget() {
        int percentage = routingState.effectiveTrafficPercentage();
        if (percentage <= 0) {
            return TARGET_LEGACY;
        }
        if (percentage >= 100) {
            return TARGET_NEW;
        }
        // ThreadLocalRandom is safe for concurrent use and avoids contention
        int roll = ThreadLocalRandom.current().nextInt(100);
        return roll < percentage ? TARGET_NEW : TARGET_LEGACY;
    }
}
