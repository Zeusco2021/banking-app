package com.bank.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mTLS configuration for Istio service mesh (Req 12.1) and TLS 1.3 for external endpoints (Req 12.2).
 *
 * Inter-service communication within the EKS cluster is secured via Istio PeerAuthentication
 * in STRICT mode (see istio/peer-authentication.yaml). DestinationRules enforce ISTIO_MUTUAL
 * TLS for all service-to-service calls (see istio/destination-rules.yaml).
 *
 * External endpoints use TLS 1.3 configured via server.ssl in each service's application.yml.
 */
@Configuration
@EnableConfigurationProperties(IstioMtlsConfig.TlsProperties.class)
public class IstioMtlsConfig {

    /**
     * TLS properties bound from server.ssl in application.yml.
     */
    @ConfigurationProperties(prefix = "server.ssl")
    public static class TlsProperties {

        private boolean enabled;
        private String protocol;
        private String enabledProtocols;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getEnabledProtocols() {
            return enabledProtocols;
        }

        public void setEnabledProtocols(String enabledProtocols) {
            this.enabledProtocols = enabledProtocols;
        }
    }

    @Bean
    public TlsProperties tlsProperties() {
        return new TlsProperties();
    }
}
