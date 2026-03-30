package com.bank.transaction.repository;

import com.bank.shared.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findBySourceAccountIdAndProcessedAtBetween(
            String sourceAccountId, LocalDateTime from, LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.transactionId = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") String transactionId);
}
