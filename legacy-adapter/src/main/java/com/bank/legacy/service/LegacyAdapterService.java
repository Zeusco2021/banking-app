package com.bank.legacy.service;

import com.bank.legacy.config.LegacyConfig;
import com.bank.legacy.discovery.ServiceRegistry;
import com.bank.legacy.featureflag.FeatureFlagService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Routes incoming requests either to the new microservice (when the module is migrated)
 * or to the legacy WebLogic monolith (when not yet migrated), implementing the
 * Strangler Fig pattern.
 *
 * <p>Circuit Breaker (Resilience4j) protects calls to the legacy monolith:
 * opens after 5 consecutive failures and returns HTTP 503 with a descriptive message.
 * A 3-second TimeLimiter enforces the timeout on each call.
 *
 * <p>Requisitos: 7.1, 7.2, 7.3, 10.3
 */
@Service
public class LegacyAdapterService {

    private static final Logger log = LoggerFactory.getLogger(LegacyAdapterService.class);

    private final LegacyConfig legacyConfig;
    private final RestTemplate legacyRestTemplate;
    private final FeatureFlagService featureFlagService;
    private final ServiceRegistry serviceRegistry;

    public LegacyAdapterService(LegacyConfig legacyConfig,
                                 RestTemplate legacyRestTemplate,
                                 FeatureFlagService featureFlagService,
                                 ServiceRegistry serviceRegistry) {
        this.legacyConfig = legacyConfig;
        this.legacyRestTemplate = legacyRestTemplate;
        this.featureFlagService = featureFlagService;
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Routes a request to either the new microservice or the legacy monolith based on
     * the migration flag for the given module.
     *
     * <p>If the module is migrated AND a service URL is registered, the request is
     * forwarded to the new microservice directly (no circuit breaker needed — new
     * services are expected to be healthy).
     *
     * <p>If the module is NOT migrated, the request is forwarded to the legacy monolith
     * with Circuit Breaker + TimeLimiter protection.
     *
     * @param moduleName the module path segment (e.g. "accounts", "transactions")
     * @param request    the incoming HTTP request
     * @return the response from whichever system handled the request
     */
    @CircuitBreaker(name = "legacyMonolith", fallbackMethod = "legacyFallback")
    @TimeLimiter(name = "legacyMonolith")
    public CompletableFuture<ResponseEntity<Object>> routeRequest(
            String moduleName, HttpServletRequest request) {

        return CompletableFuture.supplyAsync(() -> {
            if (featureFlagService.isModuleMigrated(moduleName)
                    && serviceRegistry.isRegistered(moduleName)) {
                return forwardToMicroservice(moduleName, request);
            }
            return forwardToLegacy(moduleName, request);
        });
    }

    /**
     * Forwards the request to the new microservice via service discovery.
     * Preserves the original HTTP method, headers and body.
     *
     * Requisito 7.1
     */
    private ResponseEntity<Object> forwardToMicroservice(
            String moduleName, HttpServletRequest request) {

        String serviceUrl = serviceRegistry.resolve(moduleName);
        String targetUrl = serviceUrl + buildPath(request, moduleName);
        log.info("Routing module='{}' to new microservice: {}", moduleName, targetUrl);

        HttpHeaders headers = copyHeaders(request);
        HttpEntity<byte[]> entity = new HttpEntity<>(readBody(request), headers);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        return legacyRestTemplate.exchange(targetUrl, method, entity, Object.class);
    }

    /**
     * Forwards the request to the legacy WebLogic monolith, maintaining the same
     * response contract so callers cannot distinguish which system responded.
     *
     * Requisito 7.2
     */
    private ResponseEntity<Object> forwardToLegacy(
            String moduleName, HttpServletRequest request) {

        String legacyUrl = legacyConfig.getWeblogic().getUrl()
                + "/legacy/" + moduleName
                + buildPath(request, moduleName);
        log.info("Routing module='{}' to legacy monolith: {}", moduleName, legacyUrl);

        HttpHeaders headers = copyHeaders(request);
        HttpEntity<byte[]> entity = new HttpEntity<>(readBody(request), headers);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        return legacyRestTemplate.exchange(legacyUrl, method, entity, Object.class);
    }

    /**
     * Fallback invoked when the circuit breaker is open or a timeout/exception occurs.
     * Returns HTTP 503 with a descriptive message — Requisito 7.3.
     */
    public CompletableFuture<ResponseEntity<Object>> legacyFallback(
            String moduleName, HttpServletRequest request, Throwable cause) {
        log.warn("Circuit breaker open for module='{}': {}", moduleName, cause.getMessage());
        ResponseEntity<Object> response = ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Legacy system unavailable. Circuit breaker is open. Please try again later.");
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Returns true if the given module has been migrated to a new microservice.
     * Delegates to FeatureFlagService — Requisito 7.1.
     */
    public boolean isModuleMigrated(String moduleName) {
        return featureFlagService.isModuleMigrated(moduleName);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds the path suffix after the module segment. */
    private String buildPath(HttpServletRequest request, String moduleName) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/" + moduleName);
        if (idx >= 0) {
            String suffix = uri.substring(idx + moduleName.length() + 1);
            String query = request.getQueryString();
            return suffix + (query != null ? "?" + query : "");
        }
        return "";
    }

    /** Copies all incoming headers to the outbound request. */
    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.add(name, request.getHeader(name)));
        return headers;
    }

    /** Reads the request body bytes; returns empty array on failure. */
    private byte[] readBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (Exception e) {
            log.debug("Could not read request body: {}", e.getMessage());
            return new byte[0];
        }
    }
}
