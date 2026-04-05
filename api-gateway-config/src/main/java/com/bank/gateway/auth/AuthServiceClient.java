package com.bank.gateway.auth;

import com.bank.shared.model.TokenResponse;
import com.bank.shared.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client that delegates JWT validation to the Auth Service.
 *
 * <p>Calls {@code GET /v1/auth/validate} with the Bearer token in the
 * {@code Authorization} header. Returns a {@link TokenValidationResult}
 * indicating whether the token is valid and, if so, the user identity.
 *
 * <p>Requisitos: 1.2, 1.3, 1.5
 */
@Component
public class AuthServiceClient implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    @Value("${auth-service.validate-url:http://localhost:8081/v1/auth/validate}")
    private String validateUrl;

    @Value("${auth-service.base-url:http://localhost:8081}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public AuthServiceClient(RestTemplate authRestTemplate) {
        this.restTemplate = authRestTemplate;
    }

    /**
     * Validates a JWT token by calling the Auth Service validate endpoint.
     * Returns {@code valid=false} for any error (expired, invalid, service unavailable).
     */
    @Override
    public TokenValidationResult validateToken(String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<TokenValidationResult> response = restTemplate.exchange(
                    validateUrl,
                    HttpMethod.GET,
                    entity,
                    TokenValidationResult.class
            );

            TokenValidationResult result = response.getBody();
            return result != null ? result : new TokenValidationResult(false, null, null);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.debug("Auth Service returned 401 for token validation");
            } else {
                log.warn("Auth Service returned {} during token validation", e.getStatusCode());
            }
            return new TokenValidationResult(false, null, null);
        } catch (Exception e) {
            log.warn("Auth Service unreachable during token validation: {}", e.getMessage());
            // Fail closed: reject the request if auth service is unavailable
            return new TokenValidationResult(false, null, null);
        }
    }

    // -------------------------------------------------------------------------
    // Remaining AuthService methods — not used by the gateway
    // -------------------------------------------------------------------------

    @Override
    public TokenResponse authenticate(AuthRequest request) {
        throw new UnsupportedOperationException("Gateway does not authenticate directly");
    }

    @Override
    public void revokeToken(String jwtToken) {
        throw new UnsupportedOperationException("Gateway does not revoke tokens directly");
    }

    @Override
    public RefreshTokenResponse refreshToken(String refreshToken) {
        throw new UnsupportedOperationException("Gateway does not refresh tokens directly");
    }
}
