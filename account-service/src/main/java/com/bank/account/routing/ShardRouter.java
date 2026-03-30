package com.bank.account.routing;

import org.springframework.stereotype.Component;

@Component
public class ShardRouter {

    private static final int NUM_SHARDS = 4;

    public int getShardKey(String accountId) {
        return Math.abs(accountId.hashCode()) % NUM_SHARDS;
    }
}
