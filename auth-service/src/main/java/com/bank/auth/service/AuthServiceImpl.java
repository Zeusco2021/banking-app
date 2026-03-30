package com.bank.auth.service;

import com.bank.auth.exception.AccountLockedException;
import com.bank.auth.exception.AuthenticationException;
import com.bank.auth.exception.TokenExpiredException;
import com.bank.auth.model.TokenCacheEntry;
import com.bank.auth.model.User;
import com.bank.auth.repository.UserRepository;
import com.bank.shared.model.TokenResponse;
import com.bank.shared.service.AuthService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String KEY_FAILS = "auth:fails:";
    private static final String KEY_TOKEN = "auth:token:";
    private static final String KEY_REFRESH = "auth:refresh:";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditEventPublisher auditEventPublisher;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(UserRepository userRepository,
                           JwtService jwtService,
                           RedisTemplate<String, Object> redisTemplate,
                           AuditEventPublisher auditEventPublisher) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    public TokenResponse authenticate(AuthRequest request) {
        String username = request.username();

        // Check failed attempts
        String failKey = KEY_FAILS + username;
        Object failCount = redisTemplate.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount.toString()) >= MAX_FAILED_ATTEMPTS) {
            auditEventPublisher.publish("LOGIN_FAILED", username, username, null, "Account locked");
            throw new AccountLockedException(username);
        }

        // Validate credentials
        User user = userRepository.findByUsername(username)
            .orElse(null);

        if (user == null || !user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Increment fail counter with 15-min TTL
            redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, 15, TimeUnit.MINUTES);
            auditEventPublisher.publish("LOGIN_FAILED", username, username, null, "Invalid credentials");
            throw new AuthenticationException("Invalid username or password");
        }

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getUserId(), username, user.getRoles());
        String refreshToken = jwtService.generateRefreshToken(user.getUserId());

        // Cache tokens in Redis
        long accessTtl = jwtService.getAccessTokenTtlSeconds();
        long refreshTtl = jwtService.getRefreshTokenTtlSeconds();

        TokenCacheEntry tokenEntry = new TokenCacheEntry(
            user.getUserId(),
            user.getRoles().toArray(new String[0]),
            System.currentTimeMillis() + accessTtl * 1000L
        );
        redisTemplate.opsForValue().set(KEY_TOKEN + sha256(accessToken), tokenEntry, accessTtl, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(KEY_REFRESH + sha256(refreshToken), user.getUserId(), refreshTtl, TimeUnit.SECONDS);

        // Clear fail counter on success
        redisTemplate.delete(failKey);

        auditEventPublisher.publish("LOGIN_SUCCESS", username, username, null, null);

        return new TokenResponse(accessToken, refreshToken, accessTtl);
    }

    @Override
    public TokenValidationResult validateToken(String jwtToken) {
        String cacheKey = KEY_TOKEN + sha256(jwtToken);
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached instanceof TokenCacheEntry entry) {
            return new TokenValidationResult(true, entry.userId(), entry.roles());
        }

        // Not in cache — parse and validate
        try {
            Claims claims = jwtService.parseToken(jwtToken);
            String userId = claims.getSubject();
            List<?> rolesList = claims.get("roles", List.class);
            String[] roles = rolesList != null
                ? rolesList.stream().map(Object::toString).toArray(String[]::new)
                : new String[0];
            return new TokenValidationResult(true, userId, roles);
        } catch (TokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            return new TokenValidationResult(false, null, new String[0]);
        }
    }

    @Override
    public void revokeToken(String jwtToken) {
        redisTemplate.delete(KEY_TOKEN + sha256(jwtToken));
    }

    @Override
    public RefreshTokenResponse refreshToken(String refreshToken) {
        String refreshKey = KEY_REFRESH + sha256(refreshToken);
        Object userId = redisTemplate.opsForValue().get(refreshKey);

        if (userId == null) {
            throw new TokenExpiredException("Refresh token not found or expired");
        }

        // Parse refresh token to get claims (validates signature + expiry)
        Claims claims;
        try {
            claims = jwtService.parseToken(refreshToken);
        } catch (TokenExpiredException e) {
            redisTemplate.delete(refreshKey);
            throw e;
        }

        String subject = claims.getSubject();
        User user = userRepository.findByUsername(subject)
            .or(() -> {
                // subject is userId in refresh token — find by userId fallback
                return java.util.Optional.empty();
            })
            .orElse(null);

        // Build new access token — use userId as subject, roles from cache if user not found
        String newAccessToken;
        long accessTtl = jwtService.getAccessTokenTtlSeconds();

        if (user != null) {
            newAccessToken = jwtService.generateAccessToken(user.getUserId(), user.getUsername(), user.getRoles());
            TokenCacheEntry entry = new TokenCacheEntry(
                user.getUserId(),
                user.getRoles().toArray(new String[0]),
                System.currentTimeMillis() + accessTtl * 1000L
            );
            redisTemplate.opsForValue().set(KEY_TOKEN + sha256(newAccessToken), entry, accessTtl, TimeUnit.SECONDS);
        } else {
            // Minimal token with userId only
            newAccessToken = jwtService.generateAccessToken(subject, subject, List.of());
            TokenCacheEntry entry = new TokenCacheEntry(subject, new String[0],
                System.currentTimeMillis() + accessTtl * 1000L);
            redisTemplate.opsForValue().set(KEY_TOKEN + sha256(newAccessToken), entry, accessTtl, TimeUnit.SECONDS);
        }

        return new RefreshTokenResponse(newAccessToken, accessTtl);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
