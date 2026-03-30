package com.bank.transaction.exception;

/**
 * Thrown when a transaction cannot be found by its ID.
 * Results in HTTP 404.
 */
public class TransactionNotFoundException extends RuntimeException {

    private final String transactionId;

    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
        this.transactionId = transactionId;
    }

    public String getTransactionId() { return transactionId; }
}
