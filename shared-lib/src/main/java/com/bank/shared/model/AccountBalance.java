package com.bank.shared.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AccountBalance {

    private String accountId;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime lastUpdated;

    public AccountBalance() {}

    public AccountBalance(String accountId, BigDecimal balance, String currency, LocalDateTime lastUpdated) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
        this.lastUpdated = lastUpdated;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
