package com.bank.legacy.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple service registry that maps module names to their microservice base URLs.
 * URLs are loaded from application configuration (environment variables / Secrets Manager).
 *
 * Example configuration:
 *   service-registry:
 *     services:
 *       accounts: http://account-service:8083
 *       transactions: http://transaction-service:8084
 *       auth: http://auth-service:8081
 *
 * Requisitos: 7.1
 */
@Component
@ConfigurationProperties(prefix = "service-registry")
public class ServiceRegistry {

    private Map<String, String> services = new HashMap<>();

    public Map<String, String> getServices() {
        return services;
    }

    public void setServices(Map<String, String> services) {
        this.services = services;
    }

    /**
     * Resolves the base URL for a migrated module.
     *
     * @param moduleName the module identifier (e.g. "accounts")
     * @return the base URL of the corresponding microservice
     * @throws IllegalArgumentException if no service is registered for the module
     */
    public String resolve(String moduleName) {
        String url = services.get(moduleName);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("No service registered for module: " + moduleName);
        }
        return url;
    }

    /**
     * Returns true if a service URL is registered for the given module.
     */
    public boolean isRegistered(String moduleName) {
        return services.containsKey(moduleName) && !services.get(moduleName).isBlank();
    }
}
