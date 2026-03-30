package com.bank.notification.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures correlationId and traceId are present in MDC for every log entry.
 * Reads correlationId from the incoming request header; generates one if absent.
 * Requisito 11.3
 */
@Configuration
public class LoggingConfig {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Bean
    public Filter correlationIdMdcFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                try {
                    String correlationId = null;
                    if (request instanceof HttpServletRequest httpRequest) {
                        correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
                    }
                    if (correlationId == null || correlationId.isBlank()) {
                        correlationId = UUID.randomUUID().toString();
                    }
                    MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
                    MDC.put(TRACE_ID_MDC_KEY, correlationId);
                    chain.doFilter(request, response);
                } finally {
                    MDC.remove(CORRELATION_ID_MDC_KEY);
                    MDC.remove(TRACE_ID_MDC_KEY);
                }
            }
        };
    }
}
