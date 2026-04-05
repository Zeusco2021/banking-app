package com.bank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures the RestTemplate used by {@link com.bank.gateway.auth.AuthServiceClient}
 * to call the Auth Service validate endpoint.
 *
 * <p>Requisitos: 1.2, 1.3
 */
@Configuration
public class RestTemplateConfig {

    @Bean(name = "authRestTemplate")
    public RestTemplate authRestTemplate() {
        return new RestTemplate();
    }
}
