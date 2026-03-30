package com.bank.gateway.ratelimit;

/**
 * Result of a rate limit check.
 *
 * @param allowed            true if the request is within quota
 * @param retryAfterSeconds  seconds until the current window resets (0 if allowed)
 */
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
}
