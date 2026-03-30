package com.bank.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtKeyRotationService keyRotationService;

    @Value("${jwt.access-token-ttl-minutes:15}")
    private long accessTokenTtlMinutes;

    @Value("${jwt.refresh-token-ttl-days:7}")
    private long refreshTokenTtlDays;

    @Value("${jwt.issuer:banking-platform}")
    private String issuer;

    public JwtService(JwtKeyRotationService keyRotationService) {
        this.keyRotationService = keyRotationService;
    }

    public String generateAccessToken(String userId, String username, List<String> roles) {
        long nowMs = System.currentTimeMillis();
        Date expiry = new Date(nowMs + accessTokenTtlMinutes * 60 * 1000L);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(issuer)
            .subject(userId)
            .claim("username", username)
            .claim("roles", roles)
            .issuedAt(new Date(nowMs))
            .expiration(expiry)
            .signWith(keyRotationService.getActivePrivateKey(), Jwts.SIG.RS256)
            .compact();
    }

    public String generateRefreshToken(String userId) {
        long nowMs = System.currentTimeMillis();
        Date expiry = new Date(nowMs + refreshTokenTtlDays * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(issuer)
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(new Date(nowMs))
            .expiration(expiry)
            .signWith(keyRotationService.getActivePrivateKey(), Jwts.SIG.RS256)
            .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlDays * 24 * 60 * 60;
    }

    /**
     * Parse and validate a JWT. Tries active key first, then falls back to previous key.
     * Returns Claims on success, throws TokenExpiredException or JwtException on failure.
     */
    public Claims parseToken(String token) {
        // Try active key first
        try {
            return parseClaims(token, keyRotationService.getActivePublicKey());
        } catch (ExpiredJwtException e) {
            throw new com.bank.auth.exception.TokenExpiredException("Token has expired");
        } catch (JwtException e) {
            log.debug("Active key validation failed, trying previous key: {}", e.getMessage());
        }

        // Fall back to previous key
        try {
            return parseClaims(token, keyRotationService.getPreviousPublicKey());
        } catch (ExpiredJwtException e) {
            throw new com.bank.auth.exception.TokenExpiredException("Token has expired");
        } catch (JwtException e) {
            throw new com.bank.auth.exception.AuthenticationException("Invalid token");
        }
    }

    private Claims parseClaims(String token, PublicKey publicKey) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
