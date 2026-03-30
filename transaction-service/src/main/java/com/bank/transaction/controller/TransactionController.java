package com.bank.transaction.controller;

import com.bank.shared.model.Transaction;
import com.bank.shared.model.TransactionResult;
import com.bank.shared.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResult> processTransaction(
            @Valid @RequestBody TransactionRequest request) {
        TransactionService.TransactionRequest serviceRequest = new TransactionService.TransactionRequest(
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount(),
                Transaction.TransactionType.TRANSFER,
                request.idempotencyKey(),
                request.correlationId()
        );
        TransactionResult result = transactionService.processTransaction(serviceRequest);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String transactionId) {
        return ResponseEntity.ok(transactionService.getTransaction(transactionId));
    }

    @GetMapping("/history/{accountId}")
    public ResponseEntity<List<Transaction>> getTransactionHistory(
            @PathVariable String accountId,
            @RequestParam LocalDateTime from,
            @RequestParam LocalDateTime to) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(
                accountId, new TransactionService.DateRange(from, to)));
    }

    @PostMapping("/{transactionId}/reverse")
    public ResponseEntity<TransactionResult> reverseTransaction(
            @PathVariable String transactionId,
            @RequestParam(required = false, defaultValue = "") String reason) {
        return ResponseEntity.ok(transactionService.reverseTransaction(transactionId, reason));
    }

    public record TransactionRequest(
            @NotBlank String sourceAccountId,
            @NotBlank String targetAccountId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String idempotencyKey,
            String correlationId
    ) {}
}
