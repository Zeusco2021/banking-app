package com.bank.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps an HttpServletRequest to inject authenticated user identity headers
 * without mutating the original request object.
 *
 * <p>Adds {@code X-User-Id} and {@code X-User-Roles} headers so downstream
 * microservices can trust the authenticated identity without re-validating the JWT.
 */
class AuthenticatedRequestWrapper extends HttpServletRequestWrapper {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final Map<String, String> extraHeaders;

    AuthenticatedRequestWrapper(HttpServletRequest request, String userId, List<String> roles) {
        super(request);
        extraHeaders = new HashMap<>();
        if (userId != null && !userId.isBlank()) {
            extraHeaders.put(HEADER_USER_ID, userId);
        }
        if (roles != null && !roles.isEmpty()) {
            extraHeaders.put(HEADER_USER_ROLES, String.join(",", roles));
        }
    }

    @Override
    public String getHeader(String name) {
        if (extraHeaders.containsKey(name)) {
            return extraHeaders.get(name);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (extraHeaders.containsKey(name)) {
            return Collections.enumeration(Collections.singletonList(extraHeaders.get(name)));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        names.addAll(extraHeaders.keySet());
        return Collections.enumeration(names);
    }
}
