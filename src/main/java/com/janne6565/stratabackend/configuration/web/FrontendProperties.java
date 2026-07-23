package com.janne6565.stratabackend.configuration.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Frontend location, bound from {@code strata.frontend}. Used to build the browser redirect targets
 * after an OAuth callback (success and error).
 */
@Validated
@ConfigurationProperties("strata.frontend")
public record FrontendProperties(@NotBlank String url) {}
