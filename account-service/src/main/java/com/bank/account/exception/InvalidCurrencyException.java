package com.bank.account.exception;

public class InvalidCurrencyException extends RuntimeException {

    public InvalidCurrencyException(String currency) {
        super("Invalid ISO 4217 currency code: " + currency);
    }
}
