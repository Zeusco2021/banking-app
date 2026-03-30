package com.bank.legacy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;

/**
 * Provides the AWS AppConfigData client used by FeatureFlagService.
 * Credentials are resolved from the default AWS credential chain
 * (IAM role / environment / Secrets Manager) — Requisito 12.3.
 */
@Configuration
public class AppConfigClientConfig {

    @Value("${spring.cloud.aws.region.static:us-east-1}")
    private String awsRegion;

    @Bean
    public AppConfigDataClient appConfigDataClient() {
        return AppConfigDataClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
