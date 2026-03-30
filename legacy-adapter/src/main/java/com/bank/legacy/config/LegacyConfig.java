package com.bank.legacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "legacy")
public class LegacyConfig {

    private Weblogic weblogic = new Weblogic();

    public Weblogic getWeblogic() {
        return weblogic;
    }

    public void setWeblogic(Weblogic weblogic) {
        this.weblogic = weblogic;
    }

    public static class Weblogic {
        private String url = "http://localhost:7001";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    @Bean
    public RestTemplate legacyRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
}
