package com.bank.shared.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTIONS")
public class Transaction {

    public enum TransactionType {
        TRANSFER, DEPOSIT, WITHDRAWAL, REVERSAL
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, REVERSED
    }

    @Id
    @Column(name = "TRANSACTION_ID", nullable = false, updatable = false)
    private String transactionId;

    @Column(name = "SOURCE_ACCOUNT_ID", nullable = false)
    private String sourceAccountId;

    @Column(name = "TARGET_ACCOUNT_ID")
    private String targetAccountId;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private TransactionStatus status;

    @Column(name = "IDEMPOTENCY_KEY", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "CORRELATION_ID")
    private String correlationId;

    @Column(name = "PROCESSED_AT")
    private LocalDateTime processedAt;

    @Column(name = "SHARD_KEY")
    private int shardKey;

    public Transaction() {}

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public String getTargetAccountId() { return targetAccountId; }
    public void setTargetAccountId(String targetAccountId) { this.targetAccountId = targetAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public int getShardKey() { return shardKey; }
    public void setShardKey(int shardKey) { this.shardKey = shardKey; }
}
