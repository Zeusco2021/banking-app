package com.bank.account.service;

import com.bank.account.exception.AccountNotFoundException;
import com.bank.account.exception.InvalidCurrencyException;
import com.bank.account.exception.NegativeBalanceException;
import com.bank.account.repository.AccountRepository;
import com.bank.account.routing.ShardRouter;
import com.bank.shared.model.Account;
import com.bank.shared.model.AccountBalance;
import com.bank.shared.service.AccountService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private static final String CACHE_ACCOUNT = "account:";
    private static final String CACHE_BALANCE = "account:balance:";
    private static final long CACHE_TTL_SECONDS = 30L;

    private static final Set<String> VALID_CURRENCIES = Currency.getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    private final AccountRepository accountRepository;
    private final ShardRouter shardRouter;
    private final RedisTemplate<String, Object> redisTemplate;

    public AccountServiceImpl(AccountRepository accountRepository,
                               ShardRouter shardRouter,
                               RedisTemplate<String, Object> redisTemplate) {
        this.accountRepository = accountRepository;
        this.shardRouter = shardRouter;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Account createAccount(CreateAccountRequest request) {
        validateCurrency(request.currency());

        if (request.type() != Account.AccountType.CREDIT
                && request.initialBalance() != null
                && request.initialBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegativeBalanceException();
        }

        String accountId = UUID.randomUUID().toString();

        Account account = new Account();
        account.setAccountId(accountId);
        account.setCustomerId(request.customerId());
        account.setType(request.type());
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO);
        account.setCurrency(request.currency());
        account.setCreatedAt(LocalDateTime.now());
        account.setShardKey(shardRouter.getShardKey(accountId));

        return accountRepository.save(account);
    }

    @Override
    public Account getAccount(String accountId) {
        String cacheKey = CACHE_ACCOUNT + accountId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Account account) {
            return account;
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        redisTemplate.opsForValue().set(cacheKey, account, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        return account;
    }

    @Override
    public List<Account> getAccountsByCustomer(String customerId) {
        return accountRepository.findByCustomerId(customerId);
    }

    @Override
    public Account updateAccount(String accountId, UpdateAccountRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (request.currency() != null) {
            validateCurrency(request.currency());
            account.setCurrency(request.currency());
        }
        if (request.status() != null) {
            account.setStatus(request.status());
        }
        account.setUpdatedAt(LocalDateTime.now());

        Account saved = accountRepository.save(account);
        evictCache(accountId);
        return saved;
    }

    @Override
    public void closeAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.setStatus(Account.AccountStatus.CLOSED);
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
        evictCache(accountId);
    }

    @Override
    public AccountBalance getBalance(String accountId) {
        String cacheKey = CACHE_BALANCE + accountId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof AccountBalance balance) {
            return balance;
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        AccountBalance balance = new AccountBalance(
                account.getAccountId(),
                account.getBalance(),
                account.getCurrency(),
                account.getUpdatedAt() != null ? account.getUpdatedAt() : account.getCreatedAt()
        );

        redisTemplate.opsForValue().set(cacheKey, balance, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        return balance;
    }

    private void validateCurrency(String currency) {
        if (currency == null || !VALID_CURRENCIES.contains(currency)) {
            throw new InvalidCurrencyException(currency);
        }
    }

    private void evictCache(String accountId) {
        redisTemplate.delete(CACHE_ACCOUNT + accountId);
        redisTemplate.delete(CACHE_BALANCE + accountId);
    }
}
