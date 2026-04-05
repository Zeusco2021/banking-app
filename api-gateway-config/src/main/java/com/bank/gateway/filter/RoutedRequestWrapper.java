package com.bank.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps an HttpServletRequest to inject the resolved downstream base URL header
 * so that the proxy/forwarding layer knows where to send the request.
 */
class RoutedRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> extraHeaders;

    RoutedRequestWrapper(HttpServletRequest request, String downstreamBaseUrl) {
        super(request);
        extraHeaders = new HashMap<>();
        extraHeaders.put(VersionedRoutingFilter.DOWNSTREAM_URL_HEADER, downstreamBaseUrl);
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
