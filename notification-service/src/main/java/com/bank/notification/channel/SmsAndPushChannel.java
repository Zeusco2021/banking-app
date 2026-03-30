package com.bank.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Sends SMS and push notifications via AWS SNS.
 * Credentials are injected at runtime from AWS Secrets Manager.
 */
@Component
public class SmsAndPushChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsAndPushChannel.class);

    private final SnsClient snsClient;

    public SmsAndPushChannel(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    /**
     * Sends an SMS message via AWS SNS to a phone number.
     *
     * @param phoneNumber E.164 format phone number (e.g. +15551234567)
     * @param message     SMS body text
     * @throws RuntimeException if SNS is unavailable or returns an error
     */
    public void sendSms(String phoneNumber, String message) {
        log.debug("Sending SMS via SNS to={}", phoneNumber);

        PublishRequest request = PublishRequest.builder()
                .phoneNumber(phoneNumber)
                .message(message)
                .build();

        snsClient.publish(request);
        log.info("SMS sent via SNS to={}", phoneNumber);
    }

    /**
     * Sends a push notification via AWS SNS to a device endpoint ARN.
     *
     * @param endpointArn SNS endpoint ARN for the device
     * @param message     push notification payload
     * @throws RuntimeException if SNS is unavailable or returns an error
     */
    public void sendPush(String endpointArn, String message) {
        log.debug("Sending push notification via SNS to endpointArn={}", endpointArn);

        PublishRequest request = PublishRequest.builder()
                .targetArn(endpointArn)
                .message(message)
                .build();

        snsClient.publish(request);
        log.info("Push notification sent via SNS to endpointArn={}", endpointArn);
    }
}
