package com.bank.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Configures AWS SES and SNS clients.
 * Credentials are resolved via the default AWS credential chain
 * (IAM role on EKS pod, or Secrets Manager at runtime).
 */
@Configuration
public class AwsClientConfig {

    @Value("${spring.cloud.aws.region.static:us-east-1}")
    private String awsRegion;

    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
