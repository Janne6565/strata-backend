package com.janne6565.stratabackend.services.auth.oauth;

import com.janne6565.stratabackend.model.exception.BadGatewayException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Authentik OIDC provider. Reads the standard userinfo claims {@code sub}, {@code email}, {@code
 * preferred_username} and {@code groups} (a list, mapped via the profile scope; absent -> empty).
 */
@Component
public class AuthentikOAuthProvider implements OAuthProviderClient {

    public static final String PROVIDER_NAME = "authentik";

    private static final String CLAIM_SUBJECT = "sub";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_USERNAME = "preferred_username";
    private static final String CLAIM_GROUPS = "groups";

    private static final ParameterizedTypeReference<Map<String, Object>> USER_INFO_TYPE =
            new ParameterizedTypeReference<>() {};

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(
            String accessToken, RestClient restClient, String userInfoUri) {
        Map<String, Object> raw;
        try {
            raw =
                    restClient
                            .get()
                            .uri(userInfoUri)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .body(USER_INFO_TYPE);
        } catch (RestClientException ex) {
            throw new BadGatewayException("Failed to fetch user info from Authentik");
        }
        if (raw == null) {
            throw new BadGatewayException("Authentik returned no user info");
        }
        return new OAuthUserInfo(
                asString(raw.get(CLAIM_SUBJECT)),
                asString(raw.get(CLAIM_EMAIL)),
                asString(raw.get(CLAIM_USERNAME)),
                extractGroups(raw.get(CLAIM_GROUPS)));
    }

    private List<String> extractGroups(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
