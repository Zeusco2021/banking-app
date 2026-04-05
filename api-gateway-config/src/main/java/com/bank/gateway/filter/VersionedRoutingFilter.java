package com.bank.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes versioned API paths ({@code /v{n}/{resource}/...}) to the correct downstream
 * microservice by setting the {@code X-Downstream-Url} header on the wrapped request.
 *
 * <p>Supported routes:
 * <ul>
 *   <li>{@code /v*/auth/**}          → auth-service</li>
 *   <li>{@code /v*/accounts/**}      → account-service</li>
 *   <li>{@code /v*/transactions/**}  → transaction-service</li>
 *   <li>{@code /v*/notifications/**} → notification-service</li>
 *   <li>{@code /v*/audit/**}         → audit-service</li>
 *   <li>{@code /v*/legacy/**}        → legacy-adapter</li>
 * </ul>
 *
 * <p>Unrecognised resources return HTTP 404.
 *
 * <p>Requisitos: 1.1, 1.2, 1.3, 1.4
 */
@Component
@Order(4)   // Runs after JwtAuthFilter (3)
public class VersionedRoutingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VersionedRoutingFilter.class);

    /** Header set on the request to indicate the resolved downstream base URL. */
    public static final String DOWNSTREAM_URL_HEADER = "X-Downstream-Url";

    /** Matches /v{n}/{resource}[/...] — captures version number and resource segment. */
    private static final Pattern VERSIONED_PATH = Pattern.compile("^/v(\\d+)/([^/]+)(/.*)?$");

    @Value("${routing.auth-service-url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${routing.account-service-url:http://localhost:8082}")
    private String accountServiceUrl;

    @Value("${routing.transaction-service-url:http://localhost:8083}")
    private String transactionServiceUrl;

    @Value("${routing.notification-service-url:http://localhost:8084}")
    private String notificationServiceUrl;

    @Value("${routing.audit-service-url:http://localhost:8085}")
    private String auditServiceUrl;

    @Value("${routing.legacy-adapter-url:http://localhost:8086}")
    private String legacyAdapterUrl;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        Matcher matcher = VERSIONED_PATH.matcher(path);

        if (!matcher.matches()) {
            // Non-versioned paths (actuator, internal) pass through without routing header
            filterChain.doFilter(request, response);
            return;
        }

        String resource = matcher.group(2).toLowerCase();
        String downstreamBase = resolveDownstream(resource);

        if (downstreamBase == null) {
            log.warn("Versioned routing: no downstream found for resource='{}' path='{}'", resource, path);
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"No route found for resource: " + resource + "\"}");
            return;
        }

        log.debug("Versioned routing: path='{}' → downstream='{}'", path, downstreamBase);

        RoutedRequestWrapper wrapped = new RoutedRequestWrapper(request, downstreamBase);
        filterChain.doFilter(wrapped, response);
    }

    private String resolveDownstream(String resource) {
        return switch (resource) {
            case "auth"          -> authServiceUrl;
            case "accounts"      -> accountServiceUrl;
            case "transactions"  -> transactionServiceUrl;
            case "notifications" -> notificationServiceUrl;
            case "audit"         -> auditServiceUrl;
            case "legacy"        -> legacyAdapterUrl;
            default              -> null;
        };
    }
}
