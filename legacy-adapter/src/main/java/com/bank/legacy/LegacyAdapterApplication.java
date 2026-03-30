package com.bank.legacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LegacyAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyAdapterApplication.class, args);
    }
}
