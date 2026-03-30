package com.bank.transaction.controller;

import com.bank.transaction.exception.InsufficientFundsException;
import com.bank.transaction.exception.TransactionNotFoundException;
import com.bank.transaction.exception.TransactionReversalException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * HTTP 422 for insufficient funds. Satisfies Requirement 4.3.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "INSUFFICIENT_FUNDS",
                "message", ex.getMessage(),
                "accountId", ex.getAccountId(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionNotFound(TransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TRANSACTION_NOT_FOUND",
                "message", ex.getMessage(),
                "transactionId", ex.getTransactionId(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * HTTP 422 for reversal of non-existent or already-reversed transactions.
     * Satisfies Requirement 4.7.
     */
    @ExceptionHandler(TransactionReversalException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionReversal(TransactionReversalException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", ex.getErrorCode(),
                "message", ex.getMessage(),
                "transactionId", ex.getTransactionId(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "errorCode", "INVALID_STATE",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "errorCode", "INTERNAL_ERROR",
                "message", "An unexpected error occurred",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
