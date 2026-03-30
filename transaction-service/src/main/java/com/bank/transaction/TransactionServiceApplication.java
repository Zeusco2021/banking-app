package com.bank.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Transaction Service — processes financial transactions with Virtual Threads,
 * distributed locking, Outbox Pattern, and Oracle DB shard routing.
 */
@SpringBootApplication
@EnableScheduling
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
