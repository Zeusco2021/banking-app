package com.bank.notification.repository;

import com.bank.notification.model.NotificationRecord;
import com.bank.shared.model.NotificationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<NotificationRecord, String> {

    List<NotificationRecord> findByCorrelationId(String correlationId);

    List<NotificationRecord> findByRecipientId(String recipientId);

    List<NotificationRecord> findByStatus(NotificationStatus.Status status);
}
