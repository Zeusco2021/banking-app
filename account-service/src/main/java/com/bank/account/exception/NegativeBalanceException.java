package com.bank.account.exception;

public class NegativeBalanceException extends RuntimeException {

    public NegativeBalanceException() {
        super("Initial balance cannot be negative for CHECKING or SAVINGS accounts");
    }
}
