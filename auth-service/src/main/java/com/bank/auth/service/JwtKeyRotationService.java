package com.bank.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class JwtKeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyRotationService.class);

    private static final String ACTIVE_SECRET = "/banking/auth-service/jwt-key-active";
    private static final String PREVIOUS_SECRET = "/banking/auth-service/jwt-key-previous";

    private final SecretsManagerClient secretsManagerClient;

    private KeyPair activeKeyPair;
    private KeyPair previousKeyPair;

    public JwtKeyRotationService(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    }

    @PostConstruct
    public void loadKeys() {
        activeKeyPair = loadKeyPairFromSecret(ACTIVE_SECRET);
        if (activeKeyPair == null) {
            log.warn("Active JWT key not found in Secrets Manager — generating ephemeral key pair (dev mode)");
            activeKeyPair = generateKeyPair();
        }

        previousKeyPair = loadKeyPairFromSecret(PREVIOUS_SECRET);
        if (previousKeyPair == null) {
            log.info("No previous JWT key found — using active key as fallback for previous");
            previousKeyPair = activeKeyPair;
        }
    }

    public PrivateKey getActivePrivateKey() {
        return activeKeyPair.getPrivate();
    }

    public PublicKey getActivePublicKey() {
        return activeKeyPair.getPublic();
    }

    public PublicKey getPreviousPublicKey() {
        return previousKeyPair.getPublic();
    }

    private KeyPair loadKeyPairFromSecret(String secretName) {
        try {
            String secretValue = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretName).build()
            ).secretString();

            // Expect JSON: {"privateKey":"<base64-pkcs8>","publicKey":"<base64-x509>"}
            String privateKeyB64 = extractJsonField(secretValue, "privateKey");
            String publicKeyB64 = extractJsonField(secretValue, "publicKey");

            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyB64))
            );
            PublicKey publicKey = kf.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64))
            );
            return new KeyPair(publicKey, privateKey);
        } catch (SecretsManagerException e) {
            log.warn("Secret {} not available: {}", secretName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to parse key from secret {}: {}", secretName, e.getMessage());
            return null;
        }
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    private String extractJsonField(String json, String field) {
        // Minimal JSON field extraction without extra dependencies
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) throw new IllegalArgumentException("Field not found: " + field);
        int colon = json.indexOf(':', idx + key.length());
        int start = json.indexOf('"', colon + 1) + 1;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
