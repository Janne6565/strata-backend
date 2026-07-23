package com.janne6565.stratabackend.configuration.oauth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Federated OIDC login configuration. Bound from {@code strata.oauth}. Each entry in {@code
 * providers} is an authorization-code provider (the pilot ships a single {@code authentik} entry);
 * {@code groups} names the Authentik groups that gate access and map to the coarse role.
 *
 * <p>{@code clientId}/{@code clientSecret} are intentionally not {@code @NotBlank} so the app still
 * boots in local dev without credentials — the OAuth endpoints simply won't authenticate until they
 * are supplied via environment.
 */
@Validated
@ConfigurationProperties("strata.oauth")
public record OAuthProperties(
        @Valid @NotNull Map<String, Provider> providers, @Valid Groups groups) {

    public record Provider(
            String clientId,
            String clientSecret,
            @NotBlank String authorizationUri,
            @NotBlank String tokenUri,
            @NotBlank String userInfoUri,
            @NotBlank String scope,
            @NotBlank String callbackUri) {}

    public record Groups(@NotBlank String admin, @NotBlank String user) {}
}
