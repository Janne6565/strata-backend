package com.janne6565.stratabackend.configuration.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auth config. Bound from {@code strata.auth}. The bootstrap owner is created on first startup only
 * when no {@code OWNER} exists yet; its password must be supplied via environment in any real
 * deployment and changed after first login. {@code cookieSecure} toggles the {@code Secure}
 * attribute on the refresh cookie (true in every real environment, false only for plain-HTTP local
 * dev).
 */
@Validated
@ConfigurationProperties("strata.auth")
public record AuthProperties(Bootstrap bootstrap, boolean cookieSecure) {

    public record Bootstrap(String username, String password) {}
}
