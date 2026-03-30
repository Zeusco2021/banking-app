package com.bank.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * Sends email notifications via AWS SES.
 * Credentials are injected at runtime from AWS Secrets Manager.
 */
@Component
public class EmailChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final SesClient sesClient;

    @Value("${notification.email.from-address:noreply@bank.com}")
    private String fromAddress;

    public EmailChannel(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    /**
     * Sends an email via AWS SES.
     *
     * @param recipientEmail the destination email address
     * @param subject        email subject
     * @param body           email body (plain text)
     * @throws RuntimeException if SES is unavailable or returns an error
     */
    public void send(String recipientEmail, String subject, String body) {
        log.debug("Sending email via SES to={} subject={}", recipientEmail, subject);

        SendEmailRequest request = SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder()
                        .toAddresses(recipientEmail)
                        .build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .text(Content.builder().data(body).charset("UTF-8").build())
                                .build())
                        .build())
                .build();

        sesClient.sendEmail(request);
        log.info("Email sent via SES to={}", recipientEmail);
    }
}
