package com.bank.auth.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String username) {
        super("Account locked due to too many failed attempts: " + username);
    }
}
