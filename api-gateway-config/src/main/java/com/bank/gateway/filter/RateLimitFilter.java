package com.bank.gateway.filter;

import com.bank.gateway.ratelimit.RateLimitResult;
import com.bank.gateway.ratelimit.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;

/**
 * Servlet filter that enforces Redis-backed rate limiting on every inbound request.
 *
 * Client identity is resolved in priority order:
 *   1. JWT {@code sub} claim extracted from the Bearer token (authenticated users)
 *   2. {@code X-API-Key} header (machine-to-machine clients)
 *   3. Remote IP address (anonymous / unauthenticated callers)
 *
 * When the quota is exceeded the filter short-circuits with HTTP 429 and sets
 * the {@code Retry-After} header to the number of seconds until the window resets
 * — Requisitos 8.1, 8.2, 8.3, 1.4.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY     = "X-API-Key";
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String HEADER_AUTH        = "Authorization";
    private static final String BEARER_PREFIX       = "Bearer ";

    private final RedisRateLimiter redisRateLimiter;

    @Value("${rate-limit.max-requests:100}")
    private int maxRequests;

    @Value("${rate-limit.window-seconds:60}")
    private int windowSeconds;

    public RateLimitFilter(RedisRateLimiter redisRateLimiter) {
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientId = resolveClientId(request);
        RateLimitResult result = redisRateLimiter.checkLimit(clientId, maxRequests, windowSeconds);

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":" + result.retryAfterSeconds() + "}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolve a stable client identifier from the request.
     */
    private String resolveClientId(HttpServletRequest request) {
        // 1. Try JWT sub claim from Bearer token
        String authHeader = request.getHeader(HEADER_AUTH);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String sub = extractJwtSub(authHeader.substring(BEARER_PREFIX.length()));
            if (sub != null && !sub.isBlank()) {
                return "jwt:" + sub;
            }
        }

        // 2. Try X-API-Key header
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey;
        }

        // 3. Fall back to remote IP
        String ip = request.getRemoteAddr();
        return "ip:" + (ip != null ? ip : "unknown");
    }

    /**
     * Extract the {@code sub} claim from a JWT without full signature verification.
     * Full verification is handled by the auth pre-filter; here we only need the
     * subject for rate-limit key bucketing.
     */
    private String extractJwtSub(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            // Decode payload (index 1) — pad to a valid Base64 length
            String payload = parts[1];
            int padding = (4 - payload.length() % 4) % 4;
            payload = payload + "=".repeat(padding);
            String json = new String(Base64.getUrlDecoder().decode(payload));

            // Simple extraction: find "sub":"<value>"
            int subIdx = json.indexOf("\"sub\"");
            if (subIdx < 0) {
                return null;
            }
            int colonIdx = json.indexOf(':', subIdx);
            int startQuote = json.indexOf('"', colonIdx + 1);
            int endQuote = json.indexOf('"', startQuote + 1);
            if (startQuote < 0 || endQuote < 0) {
                return null;
            }
            return json.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return null;
        }
    }
}
