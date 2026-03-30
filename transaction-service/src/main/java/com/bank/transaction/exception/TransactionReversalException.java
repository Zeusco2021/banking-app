package com.bank.transaction.exception;

/**
 * Thrown when a transaction reversal is rejected because the transaction
 * does not exist or is not in a reversible state (e.g., already REVERSED).
 * Results in HTTP 422 Unprocessable Entity. Satisfies Requirement 4.7.
 */
public class TransactionReversalException extends RuntimeException {

    private final String transactionId;
    private final String errorCode;

    public TransactionReversalException(String transactionId, String errorCode, String message) {
        super(message);
        this.transactionId = transactionId;
        this.errorCode = errorCode;
    }

    public String getTransactionId() { return transactionId; }
    public String getErrorCode() { return errorCode; }
}
