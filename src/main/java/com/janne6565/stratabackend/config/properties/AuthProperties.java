package com.janne6565.stratabackend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auth bootstrap config. Bound from {@code strata.auth}. The bootstrap owner is created on first
 * startup only when no {@code OWNER} exists yet; its password must be supplied via environment in
 * any real deployment and changed after first login.
 */
@Validated
@ConfigurationProperties("strata.auth")
public record AuthProperties(Bootstrap bootstrap) {

    public record Bootstrap(String username, String password) {}
}
