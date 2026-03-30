package com.bank.transaction.routing;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Spring AbstractRoutingDataSource that routes DB connections to the correct
 * Oracle shard based on the current thread-local shard key.
 *
 * Satisfies Requirement 4.10.
 */
public class ShardRoutingDataSource extends AbstractRoutingDataSource {

    private static final ThreadLocal<Integer> CURRENT_SHARD = new ThreadLocal<>();

    public static void setShardKey(int shardKey) {
        CURRENT_SHARD.set(shardKey);
    }

    public static void clearShardKey() {
        CURRENT_SHARD.remove();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return CURRENT_SHARD.get();
    }
}
