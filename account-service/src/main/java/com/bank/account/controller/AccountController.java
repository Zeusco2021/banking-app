package com.bank.account.controller;

import com.bank.shared.model.Account;
import com.bank.shared.model.AccountBalance;
import com.bank.shared.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<Account> createAccount(@RequestBody AccountService.CreateAccountRequest request) {
        Account account = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Account>> getAccountsByCustomer(@PathVariable String customerId) {
        return ResponseEntity.ok(accountService.getAccountsByCustomer(customerId));
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<Account> updateAccount(@PathVariable String accountId,
                                                  @RequestBody AccountService.UpdateAccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, request));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> closeAccount(@PathVariable String accountId) {
        accountService.closeAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalance> getBalance(@PathVariable String accountId) {
        return ResponseEntity.ok(accountService.getBalance(accountId));
    }
}
