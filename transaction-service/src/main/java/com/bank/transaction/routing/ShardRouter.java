package com.bank.transaction.routing;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * Deterministic shard router for Oracle DB.
 *
 * Preconditions:
 *   - accountId != null and non-empty
 *   - NUM_SHARDS > 0
 *
 * Postconditions:
 *   - Same accountId always resolves to the same DataSource (deterministic)
 *   - Distribution is uniform: hash(accountId) % NUM_SHARDS
 *
 * Satisfies Requirement 4.10.
 */
@Component
public class ShardRouter {

    static final int NUM_SHARDS = 4;

    private final List<DataSource> shardDataSources;

    public ShardRouter(List<DataSource> shardDataSources) {
        this.shardDataSources = shardDataSources;
    }

    /**
     * Resolves the DataSource for the given accountId deterministically.
     * The same accountId always maps to the same shard.
     */
    public DataSource resolveDataSource(String accountId) {
        int shardIndex = Math.abs(accountId.hashCode()) % NUM_SHARDS;
        return shardDataSources.get(shardIndex);
    }

    /**
     * Returns the shard key (0..NUM_SHARDS-1) for the given accountId.
     */
    public int getShardKey(String accountId) {
        return Math.abs(accountId.hashCode()) % NUM_SHARDS;
    }
}
