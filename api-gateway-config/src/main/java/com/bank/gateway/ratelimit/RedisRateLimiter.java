package com.bank.gateway.ratelimit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token Bucket rate limiter backed by Redis.
 * Uses an atomic Lua script (INCR + EXPIRE) to guarantee correctness
 * under concurrent access — Requisitos 8.1, 8.2, 8.3.
 */
@Component
public class RedisRateLimiter {

    // Atomic Lua script: increment counter, set TTL on first call, then check limit.
    // Returns {1, 0} when allowed, {0, ttl} when quota exceeded.
    private static final String RATE_LIMIT_SCRIPT = """
            local key     = KEYS[1]
            local limit   = tonumber(ARGV[1])
            local window  = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            if current > limit then
                return {0, redis.call('TTL', key)}
            end
            return {1, 0}
            """;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRateLimiter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check whether {@code clientId} is within its rate limit quota.
     *
     * @param clientId      identifier for the client (IP, user id, API key, …)
     * @param maxRequests   maximum number of requests allowed per window
     * @param windowSeconds duration of the sliding window in seconds
     * @return {@link RateLimitResult} with {@code allowed=true} when within quota,
     *         or {@code allowed=false} and {@code retryAfterSeconds} set to the
     *         remaining TTL of the current window when the quota is exceeded
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult checkLimit(String clientId, int maxRequests, int windowSeconds) {
        // Key encodes the window start so each window gets a fresh counter
        long windowStart = System.currentTimeMillis() / (windowSeconds * 1000L);
        String key = "ratelimit:" + clientId + ":" + windowStart;

        List<Long> result = (List<Long>) redisTemplate.execute(
                RedisScript.of(RATE_LIMIT_SCRIPT, List.class),
                List.of(key),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds)
        );

        if (result == null || result.size() < 2) {
            // Fail open: if Redis is unavailable, allow the request
            return new RateLimitResult(true, 0L);
        }

        boolean allowed = result.get(0) == 1L;
        long retryAfterSeconds = result.get(1);
        return new RateLimitResult(allowed, retryAfterSeconds);
    }
}
