package com.bank.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * SecretsManagerConfig
 *
 * Configures AWS Secrets Manager integration via Spring Cloud AWS.
 * All credentials, API keys and certificates are loaded exclusively from
 * AWS Secrets Manager at runtime (e.g. /banking/{service-name}/credentials).
 *
 * No credentials are stored in environment variables or source code.
 * Satisfies Requirement 12.3.
 */
@Configuration
public class SecretsManagerConfig {

    /**
     * Provides a SecretsManagerClient bean using the default credential provider chain
     * (IAM role attached to the EKS pod via IRSA — no hardcoded credentials).
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }
}
