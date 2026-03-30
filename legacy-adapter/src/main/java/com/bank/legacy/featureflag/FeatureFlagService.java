package com.bank.legacy.featureflag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls AWS AppConfig every 30 seconds to determine which modules have been migrated
 * to new microservices. The configuration is a JSON object where each key is a module
 * name and the value is a boolean indicating migration status.
 *
 * Example AppConfig document:
 * {
 *   "accounts": true,
 *   "transactions": false,
 *   "auth": true
 * }
 *
 * Requisitos: 7.1, 7.2
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final AppConfigDataClient appConfigDataClient;

    @Value("${feature-flags.app-config.application:banking-platform}")
    private String application;

    @Value("${feature-flags.app-config.environment:production}")
    private String environment;

    @Value("${feature-flags.app-config.profile:migration-flags}")
    private String configurationProfile;

    // In-memory cache of migrated module flags; updated on each poll
    private final Map<String, Boolean> migrationFlags = new ConcurrentHashMap<>();

    // Session token for incremental AppConfig polling
    private volatile String sessionToken;

    public FeatureFlagService(AppConfigDataClient appConfigDataClient) {
        this.appConfigDataClient = appConfigDataClient;
    }

    /**
     * Start an AppConfig session and load the initial configuration on startup.
     */
    @PostConstruct
    public void init() {
        try {
            StartConfigurationSessionRequest sessionRequest = StartConfigurationSessionRequest.builder()
                    .applicationIdentifier(application)
                    .environmentIdentifier(environment)
                    .configurationProfileIdentifier(configurationProfile)
                    .requiredMinimumPollIntervalInSeconds(30)
                    .build();
            sessionToken = appConfigDataClient.startConfigurationSession(sessionRequest)
                    .initialConfigurationToken();
            refreshFlags();
        } catch (Exception e) {
            log.warn("Could not initialise AppConfig session — all modules will route to legacy: {}", e.getMessage());
        }
    }

    /**
     * Polls AppConfig every 30 seconds for updated migration flags.
     */
    @Scheduled(fixedDelayString = "${feature-flags.poll-interval-ms:30000}")
    public void refreshFlags() {
        if (sessionToken == null) {
            return;
        }
        try {
            GetLatestConfigurationResponse response = appConfigDataClient.getLatestConfiguration(
                    GetLatestConfigurationRequest.builder()
                            .configurationToken(sessionToken)
                            .build());

            // Always update the session token for the next poll
            sessionToken = response.nextPollConfigurationToken();

            String content = response.configuration().asString(StandardCharsets.UTF_8);
            if (content != null && !content.isBlank()) {
                parseAndUpdateFlags(content);
            }
        } catch (Exception e) {
            log.error("Failed to refresh feature flags from AppConfig: {}", e.getMessage());
        }
    }

    /**
     * Returns true if the given module has been fully migrated to a new microservice.
     *
     * @param moduleName the module identifier (e.g. "accounts", "transactions")
     */
    public boolean isModuleMigrated(String moduleName) {
        return Boolean.TRUE.equals(migrationFlags.getOrDefault(moduleName, false));
    }

    /**
     * Returns an immutable snapshot of all current migration flags (for diagnostics).
     */
    public Map<String, Boolean> getAllFlags() {
        return Collections.unmodifiableMap(migrationFlags);
    }

    /**
     * Parses a simple JSON object of the form {"module": true/false, ...}.
     * Uses manual parsing to avoid pulling in a JSON library dependency.
     */
    private void parseAndUpdateFlags(String json) {
        migrationFlags.clear();
        // Strip braces and whitespace, then split on commas
        String stripped = json.trim().replaceAll("[{}\\s]", "");
        if (stripped.isEmpty()) {
            return;
        }
        for (String entry : stripped.split(",")) {
            String[] kv = entry.split(":");
            if (kv.length == 2) {
                String key = kv[0].replace("\"", "").trim();
                boolean value = Boolean.parseBoolean(kv[1].trim());
                migrationFlags.put(key, value);
            }
        }
        log.info("Feature flags refreshed: {}", migrationFlags);
    }
}
