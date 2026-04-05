package com.bank.gateway.filter;

import com.bank.shared.service.AuthService;
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
import java.util.Arrays;
import java.util.List;

/**
 * JWT authentication pre-filter that validates Bearer tokens on all protected endpoints.
 *
 * <p>Calls Auth Service's {@code GET /v1/auth/validate} endpoint to verify the token.
 * Expired tokens receive HTTP 401 with {@code WWW-Authenticate: Bearer error="token_expired"}.
 * Missing or invalid tokens receive HTTP 401.
 * Valid tokens propagate the {@code correlationId} header downstream.
 *
 * <p>Public paths (login, refresh, health) are excluded from JWT validation.
 *
 * <p>Requisitos: 1.2, 1.3, 1.5, 2.7
 */
@Component
@Order(3)   // Runs after RateLimitFilter (1) and CanaryRoutingFilter (2)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String HEADER_AUTH           = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_USER_ID        = "X-User-Id";
    private static final String HEADER_USER_ROLES     = "X-User-Roles";

    /** Paths that do not require JWT authentication. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/actuator/health",
            "/actuator/info",
            "/internal/canary"
    );

    @Value("${auth-service.validate-url:http://localhost:8081/v1/auth/validate}")
    private String authValidateUrl;

    private final AuthService authService;

    public JwtAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER_AUTH);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("JWT auth: missing or malformed Authorization header for path={}", request.getRequestURI());
            sendUnauthorized(response, "Bearer realm=\"banking-platform\"");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        AuthService.TokenValidationResult result;
        try {
            result = authService.validateToken(token);
        } catch (Exception e) {
            log.warn("JWT auth: token validation call failed — {}", e.getMessage());
            sendUnauthorized(response, "Bearer realm=\"banking-platform\"");
            return;
        }

        if (!result.valid()) {
            log.debug("JWT auth: invalid or expired token for path={}", request.getRequestURI());
            sendUnauthorized(response, "Bearer error=\"token_expired\"");
            return;
        }

        // Propagate user identity and correlation context downstream
        AuthenticatedRequestWrapper wrapped = new AuthenticatedRequestWrapper(request,
                result.userId(),
                result.roles() != null ? Arrays.asList(result.roles()) : List.of());

        log.debug("JWT auth: validated userId={} path={}", result.userId(), request.getRequestURI());
        filterChain.doFilter(wrapped, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String wwwAuthenticate) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", wwwAuthenticate);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }
}
