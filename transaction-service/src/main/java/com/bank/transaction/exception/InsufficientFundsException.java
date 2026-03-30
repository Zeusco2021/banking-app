package com.bank.transaction.exception;

/**
 * Thrown when a transaction is rejected due to insufficient balance.
 * Results in HTTP 422 with error code INSUFFICIENT_FUNDS. Requirement 4.3.
 */
public class InsufficientFundsException extends RuntimeException {

    private final String accountId;
    private final String required;
    private final String available;

    public InsufficientFundsException(String accountId, String required, String available) {
        super("Insufficient funds in account " + accountId
                + ": required=" + required + ", available=" + available);
        this.accountId = accountId;
        this.required = required;
        this.available = available;
    }

    public String getAccountId() { return accountId; }
    public String getRequired() { return required; }
    public String getAvailable() { return available; }
}
