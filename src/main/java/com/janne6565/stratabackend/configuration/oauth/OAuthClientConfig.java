package com.janne6565.stratabackend.configuration.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides the {@link RestClient} used for the OAuth token exchange and userinfo calls. This Spring
 * Boot setup does not auto-configure a {@code RestClient.Builder} bean, so the client is built
 * explicitly here — a single dedicated client for all outbound OAuth HTTP.
 */
@Configuration
public class OAuthClientConfig {

    @Bean
    public RestClient oAuthRestClient() {
        return RestClient.builder().build();
    }
}
