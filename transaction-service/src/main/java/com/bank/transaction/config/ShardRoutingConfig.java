package com.bank.transaction.config;

import com.bank.transaction.routing.ShardRouter;
import com.bank.transaction.routing.ShardRoutingDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configures the shard-routing DataSource for Oracle DB.
 * Each shard maps to a separate Oracle instance/schema.
 * Satisfies Requirement 4.10.
 */
@Configuration
public class ShardRoutingConfig {

    @Value("${transaction.datasource.shard0.url}")
    private String shard0Url;

    @Value("${transaction.datasource.shard1.url}")
    private String shard1Url;

    @Value("${transaction.datasource.shard2.url}")
    private String shard2Url;

    @Value("${transaction.datasource.shard3.url}")
    private String shard3Url;

    @Value("${transaction.datasource.username}")
    private String username;

    @Value("${transaction.datasource.password}")
    private String password;

    @Bean
    public List<DataSource> shardDataSources() {
        List<DataSource> sources = new ArrayList<>();
        sources.add(buildDataSource(shard0Url));
        sources.add(buildDataSource(shard1Url));
        sources.add(buildDataSource(shard2Url));
        sources.add(buildDataSource(shard3Url));
        return sources;
    }

    @Bean
    public ShardRouter shardRouter(List<DataSource> shardDataSources) {
        return new ShardRouter(shardDataSources);
    }

    @Bean
    @Primary
    public DataSource routingDataSource(List<DataSource> shardDataSources) {
        ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        for (int i = 0; i < shardDataSources.size(); i++) {
            targetDataSources.put(i, shardDataSources.get(i));
        }

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(shardDataSources.get(0));
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    private DataSource buildDataSource(String url) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("oracle.jdbc.OracleDriver")
                .build();
    }
}
