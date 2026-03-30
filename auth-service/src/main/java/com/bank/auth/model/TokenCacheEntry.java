package com.bank.auth.model;

public record TokenCacheEntry(String userId, String[] roles, long expiresAt) {}
