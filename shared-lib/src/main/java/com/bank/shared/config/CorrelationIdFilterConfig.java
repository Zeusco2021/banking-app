package com.bank.shared.config;

import com.bank.shared.filter.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link CorrelationIdFilter} as a servlet filter with highest priority
 * and applies it to all URL patterns.
 */
@Configuration
public class CorrelationIdFilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
            CorrelationIdFilter correlationIdFilter) {

        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(correlationIdFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("correlationIdFilter");
        return registration;
    }
}
