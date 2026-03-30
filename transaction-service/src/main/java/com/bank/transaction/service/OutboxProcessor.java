package com.bank.transaction.service;

import com.bank.transaction.model.OutboxEvent;
import com.bank.transaction.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic processor that retries publishing pending OUTBOX events to Kafka.
 * Runs every 10 seconds and retries until each event is successfully published.
 *
 * Satisfies Requirements 4.5, 10.6.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxProcessor(OutboxRepository outboxRepository,
                           KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Polls the OUTBOX table every 10 seconds for PENDING events and retries
     * publishing them to Kafka until successful.
     * Satisfies Requirement 10.6.
     */
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING);

        if (pending.isEmpty()) {
            return;
        }

        log.info("OutboxProcessor: found {} pending event(s) to retry", pending.size());

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventId(), event.getPayload()).get();
                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);
                log.info("OutboxProcessor: published event={} to topic={}", event.getEventId(), event.getTopic());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                outboxRepository.save(event);
                log.warn("OutboxProcessor: failed to publish event={}, retryCount={}",
                        event.getEventId(), event.getRetryCount(), e);
            }
        }
    }
}
