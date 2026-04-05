package com.bank.shared.service;

import com.bank.shared.model.Transaction;
import com.bank.shared.model.TransactionResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {

    record TransactionRequest(
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        Transaction.TransactionType type,
        String idempotencyKey,
        String correlationId,
        String currency
    ) {}

    record DateRange(LocalDateTime from, LocalDateTime to) {}

    TransactionResult processTransaction(TransactionRequest request);

    Transaction getTransaction(String transactionId);

    List<Transaction> getTransactionHistory(String accountId, DateRange range);

    TransactionResult reverseTransaction(String transactionId, String reason);
}
