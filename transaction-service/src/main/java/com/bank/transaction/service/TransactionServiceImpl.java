package com.bank.transaction.service;

import com.bank.shared.model.Account;
import com.bank.shared.model.Transaction;
import com.bank.shared.model.TransactionResult;
import com.bank.shared.service.TransactionService;
import com.bank.transaction.exception.InsufficientFundsException;
import com.bank.transaction.exception.TransactionNotFoundException;
import com.bank.transaction.exception.TransactionReversalException;
import com.bank.transaction.model.OutboxEvent;
import com.bank.transaction.repository.OutboxRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.routing.ShardRouter;
import com.bank.transaction.routing.ShardRoutingDataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Transaction Service implementation with Virtual Threads, distributed locking,
 * idempotency, Outbox Pattern, and shard routing.
 *
 * Satisfies Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10.
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private static final String CACHE_BALANCE = "account:balance:";
    private static final String IDEMPOTENCY_PREFIX = "txn:idempotency:";
    private static final String LOCK_PREFIX = "txn:lock:";
    private static final long LOCK_TTL_SECONDS = 5L;
    private static final long IDEMPOTENCY_TTL_HOURS = 24L;
    private static final String TOPIC_TRANSACTIONS_COMPLETED = "transactions.completed";

    // Lua script for SETNX-based distributed lock: SET key value NX EX ttl
    private static final String LOCK_SCRIPT = """
        return redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])
        """;

    private static final String UNLOCK_SCRIPT = """
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        else
            return 0
        end
        """;

    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ShardRouter shardRouter;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            OutboxRepository outboxRepository,
            ShardRouter shardRouter,
            RedisTemplate<String, Object> redisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.transactionRepository = transactionRepository;
        this.outboxRepository = outboxRepository;
        this.shardRouter = shardRouter;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    /**
     * Processes a financial transaction using Virtual Threads.
     *
     * Algorithm:
     *   1. Check idempotency — return cached result if already processed
     *   2. Acquire distributed lock on sourceAccountId (Redis SETNX)
     *   3. Validate sufficient balance
     *   4. Execute debit + credit in atomic DB transaction
     *   5. Publish TransactionCompleted to Kafka (async); fall back to OUTBOX if unavailable
     *   6. Invalidate balance cache for both accounts
     *   7. Store idempotency key
     *
     * Satisfies Requirements 4.1, 4.2, 4.6, 4.8, 4.9.
     */
    @Override
    public TransactionResult processTransaction(TransactionRequest request) {
        // Run on a Virtual Thread (Requirement 4.8)
        CompletableFuture<TransactionResult> future = CompletableFuture.supplyAsync(
                () -> doProcessTransaction(request), virtualThreadExecutor);
        try {
            return future.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof InsufficientFundsException ife) {
                throw ife;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Transaction processing failed", cause);
        }
    }

    private TransactionResult doProcessTransaction(TransactionRequest request) {
        // 1. Idempotency check (Requirement 4.1)
        String idempotencyKey = IDEMPOTENCY_PREFIX + request.idempotencyKey();
        Object cached = redisTemplate.opsForValue().get(idempotencyKey);
        if (cached instanceof TransactionResult result) {
            log.info("Idempotent request — returning cached result for key={}", request.idempotencyKey());
            return result;
        }

        // 2. Acquire distributed lock on sourceAccountId (Requirement 4.9)
        String lockKey = LOCK_PREFIX + request.sourceAccountId();
        String lockValue = UUID.randomUUID().toString();
        boolean locked = acquireLock(lockKey, lockValue, LOCK_TTL_SECONDS);
        if (!locked) {
            throw new RuntimeException("Could not acquire lock for account: " + request.sourceAccountId());
        }

        try {
            // Set shard routing context
            int shardKey = shardRouter.getShardKey(request.sourceAccountId());
            ShardRoutingDataSource.setShardKey(shardKey);

            // 3. Validate balance and execute transfer atomically (Requirements 4.2, 4.3)
            TransactionResult result = executeTransfer(request);

            // 4. Publish event to Kafka async; fall back to OUTBOX if unavailable (Requirements 4.4, 4.5)
            publishTransactionCompleted(result.getTransactionId(), request);

            // 5. Invalidate balance cache for both accounts (Requirement 4.6)
            redisTemplate.delete(CACHE_BALANCE + request.sourceAccountId());
            redisTemplate.delete(CACHE_BALANCE + request.targetAccountId());

            // 6. Store idempotency result (Requirement 4.1)
            redisTemplate.opsForValue().set(idempotencyKey, result, IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);

            return result;
        } finally {
            releaseLock(lockKey, lockValue);
            ShardRoutingDataSource.clearShardKey();
        }
    }

    /**
     * Executes debit and credit in a single atomic DB transaction.
     * Validates sufficient balance before modifying any balance.
     * Satisfies Requirements 4.2, 4.3.
     */
    @Transactional
    protected TransactionResult executeTransfer(TransactionRequest request) {
        // Load source account with pessimistic lock
        Account source = loadAccountForUpdate(request.sourceAccountId());

        // Validate sufficient balance (Requirement 4.3)
        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    request.sourceAccountId(),
                    request.amount().toPlainString(),
                    source.getBalance().toPlainString());
        }

        // Debit source account
        source.setBalance(source.getBalance().subtract(request.amount()));
        source.setUpdatedAt(LocalDateTime.now());
        saveAccount(source);

        // Credit target account
        Account target = loadAccountForUpdate(request.targetAccountId());
        target.setBalance(target.getBalance().add(request.amount()));
        target.setUpdatedAt(LocalDateTime.now());
        saveAccount(target);

        // Persist transaction record
        Transaction txn = new Transaction();
        txn.setTransactionId(UUID.randomUUID().toString());
        txn.setSourceAccountId(request.sourceAccountId());
        txn.setTargetAccountId(request.targetAccountId());
        txn.setAmount(request.amount());
        txn.setType(request.type());
        txn.setStatus(Transaction.TransactionStatus.COMPLETED);
        txn.setIdempotencyKey(request.idempotencyKey());
        txn.setCorrelationId(request.correlationId());
        txn.setProcessedAt(LocalDateTime.now());
        txn.setShardKey(shardRouter.getShardKey(request.sourceAccountId()));
        transactionRepository.save(txn);

        return TransactionResult.success(txn.getTransactionId());
    }

    /**
     * Reverses a completed transaction by creating a REVERSAL transaction
     * and restoring both account balances atomically.
     * Satisfies Requirement 4.7.
     */
    @Override
    @Transactional
    public TransactionResult reverseTransaction(String transactionId, String reason) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionReversalException(
                        transactionId, "TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + transactionId));

        if (original.getStatus() == Transaction.TransactionStatus.REVERSED) {
            throw new TransactionReversalException(
                    transactionId, "ALREADY_REVERSED",
                    "Transaction " + transactionId + " has already been reversed");
        }
        if (original.getStatus() != Transaction.TransactionStatus.COMPLETED) {
            throw new TransactionReversalException(
                    transactionId, "INVALID_STATE",
                    "Only COMPLETED transactions can be reversed, current status: " + original.getStatus());
        }

        int shardKey = shardRouter.getShardKey(original.getSourceAccountId());
        ShardRoutingDataSource.setShardKey(shardKey);
        try {
            // Restore source account balance
            Account source = loadAccountForUpdate(original.getSourceAccountId());
            source.setBalance(source.getBalance().add(original.getAmount()));
            source.setUpdatedAt(LocalDateTime.now());
            saveAccount(source);

            // Restore target account balance
            Account target = loadAccountForUpdate(original.getTargetAccountId());
            target.setBalance(target.getBalance().subtract(original.getAmount()));
            target.setUpdatedAt(LocalDateTime.now());
            saveAccount(target);

            // Mark original as REVERSED
            original.setStatus(Transaction.TransactionStatus.REVERSED);
            transactionRepository.save(original);

            // Create REVERSAL transaction record
            Transaction reversal = new Transaction();
            reversal.setTransactionId(UUID.randomUUID().toString());
            reversal.setSourceAccountId(original.getTargetAccountId()); // reversed direction
            reversal.setTargetAccountId(original.getSourceAccountId());
            reversal.setAmount(original.getAmount());
            reversal.setType(Transaction.TransactionType.REVERSAL);
            reversal.setStatus(Transaction.TransactionStatus.COMPLETED);
            reversal.setIdempotencyKey("reversal:" + transactionId);
            reversal.setCorrelationId(original.getCorrelationId());
            reversal.setProcessedAt(LocalDateTime.now());
            reversal.setShardKey(shardKey);
            transactionRepository.save(reversal);

            // Invalidate balance cache
            redisTemplate.delete(CACHE_BALANCE + original.getSourceAccountId());
            redisTemplate.delete(CACHE_BALANCE + original.getTargetAccountId());

            return TransactionResult.success(reversal.getTransactionId());
        } finally {
            ShardRoutingDataSource.clearShardKey();
        }
    }

    @Override
    public Transaction getTransaction(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Override
    public List<Transaction> getTransactionHistory(String accountId, DateRange range) {
        return transactionRepository.findBySourceAccountIdAndProcessedAtBetween(
                accountId, range.from(), range.to());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Publishes TransactionCompleted event to Kafka.
     * Falls back to OUTBOX table if Kafka is unavailable. Requirement 4.4, 4.5.
     */
    private void publishTransactionCompleted(String transactionId, TransactionRequest request) {
        Map<String, Object> event = Map.of(
                "transactionId", transactionId,
                "sourceAccountId", request.sourceAccountId(),
                "targetAccountId", request.targetAccountId(),
                "amount", request.amount(),
                "currency", request.currency() != null ? request.currency() : "USD",
                "correlationId", request.correlationId() != null ? request.correlationId() : "",
                "timestamp", LocalDateTime.now().toString()
        );
        try {
            kafkaTemplate.send(TOPIC_TRANSACTIONS_COMPLETED, transactionId, event);
            log.debug("Published TransactionCompleted event for txn={}", transactionId);
        } catch (Exception e) {
            log.warn("Kafka unavailable — persisting event to OUTBOX for txn={}", transactionId, e);
            persistToOutbox(transactionId, event);
        }
    }

    @Transactional
    protected void persistToOutbox(String transactionId, Object event) {
        try {
            OutboxEvent outbox = new OutboxEvent();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setTopic(TOPIC_TRANSACTIONS_COMPLETED);
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setStatus(OutboxEvent.OutboxStatus.PENDING);
            outbox.setCreatedAt(LocalDateTime.now());
            outbox.setRetryCount(0);
            outboxRepository.save(outbox);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize event for OUTBOX, txn={}", transactionId, ex);
        }
    }

    /**
     * Acquires a distributed lock using Redis SETNX (SET NX EX).
     * Satisfies Requirement 4.9.
     */
    private boolean acquireLock(String lockKey, String lockValue, long ttlSeconds) {
        Object result = redisTemplate.execute(
                RedisScript.of(LOCK_SCRIPT, String.class),
                List.of(lockKey),
                lockValue,
                String.valueOf(ttlSeconds)
        );
        return "OK".equals(result);
    }

    private void releaseLock(String lockKey, String lockValue) {
        try {
            redisTemplate.execute(
                    RedisScript.of(UNLOCK_SCRIPT, Long.class),
                    List.of(lockKey),
                    lockValue
            );
        } catch (Exception e) {
            log.warn("Failed to release lock for key={}", lockKey, e);
        }
    }

    /**
     * Loads an Account with a pessimistic write lock via JDBC for update.
     * Uses the shard-routed DataSource already set in the thread-local context.
     */
    private Account loadAccountForUpdate(String accountId) {
        // Use JPA pessimistic lock via JDBC query
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ACCOUNTS WHERE ACCOUNT_ID = ? FOR UPDATE", accountId);
        if (rows.isEmpty()) {
            throw new RuntimeException("Account not found: " + accountId);
        }
        Map<String, Object> row = rows.get(0);
        Account account = new Account();
        account.setAccountId((String) row.get("ACCOUNT_ID"));
        account.setCustomerId((String) row.get("CUSTOMER_ID"));
        account.setBalance((BigDecimal) row.get("BALANCE"));
        account.setCurrency((String) row.get("CURRENCY"));
        account.setStatus(Account.AccountStatus.valueOf((String) row.get("STATUS")));
        account.setType(Account.AccountType.valueOf((String) row.get("TYPE")));
        return account;
    }

    private void saveAccount(Account account) {
        jdbcTemplate.update(
                "UPDATE ACCOUNTS SET BALANCE = ?, UPDATED_AT = ? WHERE ACCOUNT_ID = ?",
                account.getBalance(),
                account.getUpdatedAt(),
                account.getAccountId()
        );
    }
}
