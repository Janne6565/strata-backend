package com.janne6565.stratabackend.configuration.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** JWT signing/issuing configuration. Bound from {@code strata.jwt}. */
@Validated
@ConfigurationProperties("strata.jwt")
public record JwtProperties(
        @NotBlank String secret, @NotNull Duration accessTokenTtl, @NotBlank String issuer) {}
