package com.bank.shared.model;

public class TransactionResult {

    public enum ResultStatus {
        SUCCESS, FAILED, REJECTED
    }

    private String transactionId;
    private ResultStatus status;
    private String errorCode;
    private String errorMessage;

    public TransactionResult() {}

    public static TransactionResult success(String transactionId) {
        TransactionResult result = new TransactionResult();
        result.transactionId = transactionId;
        result.status = ResultStatus.SUCCESS;
        return result;
    }

    public static TransactionResult failure(String errorCode, String errorMessage) {
        TransactionResult result = new TransactionResult();
        result.status = ResultStatus.FAILED;
        result.errorCode = errorCode;
        result.errorMessage = errorMessage;
        return result;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public ResultStatus getStatus() { return status; }
    public void setStatus(ResultStatus status) { this.status = status; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
