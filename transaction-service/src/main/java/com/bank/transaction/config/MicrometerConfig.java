package com.bank.transaction.config;

import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Micrometer metrics with Prometheus export.
 * Adds common tags (service, version) and enables JVM + HTTP server metrics.
 * Requisito 11.1
 */
@Configuration
public class MicrometerConfig {

    @Bean
    public MeterRegistryCustomizer<PrometheusMeterRegistry> prometheusCustomizer() {
        return registry -> registry.config()
                .commonTags("service", "transaction-service", "version", "1.0.0");
    }

    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }
}
