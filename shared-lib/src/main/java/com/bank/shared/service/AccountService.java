package com.bank.shared.service;

import com.bank.shared.model.Account;
import com.bank.shared.model.AccountBalance;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {

    record CreateAccountRequest(String customerId, Account.AccountType type, String currency, BigDecimal initialBalance) {}

    record UpdateAccountRequest(Account.AccountStatus status, String currency) {}

    Account createAccount(CreateAccountRequest request);

    Account getAccount(String accountId);

    List<Account> getAccountsByCustomer(String customerId);

    Account updateAccount(String accountId, UpdateAccountRequest request);

    void closeAccount(String accountId);

    AccountBalance getBalance(String accountId);
}
