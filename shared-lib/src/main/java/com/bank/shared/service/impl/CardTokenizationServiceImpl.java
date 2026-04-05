package com.bank.shared.service.impl;

import com.bank.shared.model.CardData;
import com.bank.shared.service.CardTokenizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.paymentcryptographydata.PaymentCryptographyDataClient;
import software.amazon.awssdk.services.paymentcryptographydata.model.CardVerificationAttributes;
import software.amazon.awssdk.services.paymentcryptographydata.model.DecryptDataRequest;
import software.amazon.awssdk.services.paymentcryptographydata.model.DecryptDataResponse;
import software.amazon.awssdk.services.paymentcryptographydata.model.EncryptDataRequest;
import software.amazon.awssdk.services.paymentcryptographydata.model.EncryptDataResponse;
import software.amazon.awssdk.services.paymentcryptographydata.model.EncryptionDecryptionAttributes;
import software.amazon.awssdk.services.paymentcryptographydata.model.SymmetricEncryptionAttributes;
import software.amazon.awssdk.services.paymentcryptographydata.model.EncryptionMode;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * CardTokenizationServiceImpl
 *
 * Implements card tokenization using AWS Payment Cryptography.
 * The PAN and card data are encrypted via the AWS Payment Cryptography Data Plane API
 * before any persistence. The resulting ciphertext (token) is safe to store.
 *
 * Key ARN is loaded from AWS Secrets Manager at construction time — never from
 * environment variables or source code.
 *
 * Satisfies Requirement 12.6.
 *
 * Preconditions for tokenize():
 *   - cardData != null, cardData.pan() is a valid 13–19 digit PAN
 *   - AWS Payment Cryptography key ARN is accessible in Secrets Manager
 *
 * Postconditions for tokenize():
 *   - Returns a Base64-encoded ciphertext token
 *   - Raw PAN is never logged or persisted
 *
 * Preconditions for detokenize():
 *   - token is a non-null Base64-encoded ciphertext previously produced by tokenize()
 *
 * Postconditions for detokenize():
 *   - Returns the original CardData
 */
public class CardTokenizationServiceImpl implements CardTokenizationService {

    private static final Logger log = LoggerFactory.getLogger(CardTokenizationServiceImpl.class);

    private final PaymentCryptographyDataClient dataClient;
    private final String keyArn;

    public CardTokenizationServiceImpl(
            PaymentCryptographyDataClient dataClient,
            SecretsManagerClient secretsManagerClient,
            String keyArnSecretName) {
        this.dataClient = dataClient;
        this.keyArn = loadKeyArn(secretsManagerClient, keyArnSecretName);
        log.info("CardTokenizationService initialized with key ARN from Secrets Manager secret: {}", keyArnSecretName);
    }

    /**
     * Loads the Payment Cryptography key ARN from AWS Secrets Manager.
     * This ensures no key ARN is hardcoded in source or environment variables.
     */
    private String loadKeyArn(SecretsManagerClient secretsManagerClient, String secretName) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
        return secretsManagerClient.getSecretValue(request).secretString();
    }

    /**
     * {@inheritDoc}
     *
     * Serializes card data into a pipe-delimited plaintext payload, then encrypts
     * it using AES-256-CBC via AWS Payment Cryptography. The resulting ciphertext
     * is Base64-encoded and returned as the token.
     *
     * The plaintext payload is: "PAN|expiryMonth|expiryYear|cardholderName"
     * It is held in memory only for the duration of the API call and never logged.
     */
    @Override
    public String tokenize(CardData cardData) {
        if (cardData == null) {
            throw new IllegalArgumentException("cardData must not be null");
        }

        // Build plaintext payload — never log this value
        String plaintext = String.join("|",
                cardData.pan(),
                cardData.expiryMonth(),
                cardData.expiryYear(),
                cardData.cardholderName() != null ? cardData.cardholderName() : "");

        EncryptDataRequest request = EncryptDataRequest.builder()
                .keyIdentifier(keyArn)
                .plainText(Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8)))
                .encryptionAttributes(EncryptionDecryptionAttributes.builder()
                        .symmetric(SymmetricEncryptionAttributes.builder()
                                .mode(EncryptionMode.CBC)
                                .build())
                        .build())
                .build();

        EncryptDataResponse response = dataClient.encryptData(request);
        String token = response.cipherText();

        log.debug("Card tokenized successfully (last 4: {})", cardData.pan().substring(Math.max(0, cardData.pan().length() - 4)));
        return token;
    }

    /**
     * {@inheritDoc}
     *
     * Decrypts the token using AWS Payment Cryptography and reconstructs the CardData.
     * Access to this method should be restricted to authorized services only via IAM.
     */
    @Override
    public CardData detokenize(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null or blank");
        }

        DecryptDataRequest request = DecryptDataRequest.builder()
                .keyIdentifier(keyArn)
                .cipherText(token)
                .decryptionAttributes(EncryptionDecryptionAttributes.builder()
                        .symmetric(SymmetricEncryptionAttributes.builder()
                                .mode(EncryptionMode.CBC)
                                .build())
                        .build())
                .build();

        DecryptDataResponse response = dataClient.decryptData(request);
        String plaintext = new String(
                Base64.getDecoder().decode(response.plainText()),
                StandardCharsets.UTF_8);

        String[] parts = plaintext.split("\\|", -1);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid token: decrypted payload has unexpected format");
        }

        return new CardData(
                parts[0],
                parts[1],
                parts[2],
                parts.length > 3 ? parts[3] : null);
    }
}
