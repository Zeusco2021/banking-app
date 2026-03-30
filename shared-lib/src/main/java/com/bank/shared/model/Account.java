package com.bank.shared.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNTS")
public class Account {

    public enum AccountType {
        CHECKING, SAVINGS, CREDIT
    }

    public enum AccountStatus {
        ACTIVE, SUSPENDED, CLOSED
    }

    @Id
    @Column(name = "ACCOUNT_ID", nullable = false, updatable = false)
    private String accountId;

    @Column(name = "CUSTOMER_ID", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private AccountStatus status;

    @Column(name = "BALANCE", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "SHARD_KEY")
    private int shardKey;

    public Account() {}

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getShardKey() { return shardKey; }
    public void setShardKey(int shardKey) { this.shardKey = shardKey; }
}
