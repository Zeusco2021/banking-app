package com.bank.shared.config;

import com.bank.shared.service.CardTokenizationService;
import com.bank.shared.service.impl.CardTokenizationServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.paymentcryptography.PaymentCryptographyClient;
import software.amazon.awssdk.services.paymentcryptographydata.PaymentCryptographyDataClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * CardTokenizationConfig
 *
 * Configures AWS Payment Cryptography clients and the CardTokenizationService bean.
 * The key ARN is loaded from AWS Secrets Manager at startup — never from env vars
 * or source code.
 *
 * Satisfies Requirements 12.5, 12.6.
 */
@Configuration
public class CardTokenizationConfig {

    /**
     * AWS region for Payment Cryptography (injected from application.yml or
     * overridden by Spring Cloud AWS Secrets Manager property source).
     */
    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Secret name in AWS Secrets Manager that holds the Payment Cryptography key ARN.
     * Example value: "/banking/payment-cryptography/tokenization-key-arn"
     */
    @Value("${aws.payment-cryptography.key-arn-secret-name:/banking/payment-cryptography/tokenization-key-arn}")
    private String keyArnSecretName;

    @Bean
    public PaymentCryptographyClient paymentCryptographyClient() {
        return PaymentCryptographyClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public PaymentCryptographyDataClient paymentCryptographyDataClient() {
        return PaymentCryptographyDataClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public CardTokenizationService cardTokenizationService(
            PaymentCryptographyDataClient dataClient,
            SecretsManagerClient secretsManagerClient) {
        return new CardTokenizationServiceImpl(dataClient, secretsManagerClient, keyArnSecretName);
    }
}
