package com.bank.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps an HttpServletRequest to inject the canary routing header without mutating
 * the original request object.
 */
class CanaryRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> extraHeaders;

    CanaryRequestWrapper(HttpServletRequest request, String routeTarget) {
        super(request);
        extraHeaders = new HashMap<>();
        extraHeaders.put(CanaryRoutingFilter.ROUTE_TARGET_HEADER, routeTarget);
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
        java.util.List<String> names = Collections.list(super.getHeaderNames());
        names.addAll(extraHeaders.keySet());
        return Collections.enumeration(names);
    }
}
