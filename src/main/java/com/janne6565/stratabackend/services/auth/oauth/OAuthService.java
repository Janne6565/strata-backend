package com.janne6565.stratabackend.services.auth.oauth;

import com.janne6565.stratabackend.configuration.oauth.OAuthProperties;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.exception.BadGatewayException;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Hand-rolled OIDC authorization-code flow. Builds the provider authorization URL, exchanges the
 * code for an access token, and fetches userinfo, then hands off to {@link OAuthUserResolver} to
 * resolve/create the local user in a transaction. Kept non-transactional itself: it wraps two
 * remote HTTP calls and must not hold a DB connection across them.
 */
@Slf4j
@Service
public class OAuthService {

    private static final ParameterizedTypeReference<Map<String, Object>> TOKEN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final OAuthProperties properties;
    private final OAuthStateStore stateStore;
    private final OAuthUserResolver userResolver;
    private final RestClient restClient;
    private final Map<String, OAuthProviderClient> providers;

    public OAuthService(
            OAuthProperties properties,
            OAuthStateStore stateStore,
            OAuthUserResolver userResolver,
            RestClient oAuthRestClient,
            List<OAuthProviderClient> providerClients) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.userResolver = userResolver;
        this.restClient = oAuthRestClient;
        this.providers =
                providerClients.stream()
                        .collect(
                                Collectors.toMap(
                                        OAuthProviderClient::providerName, Function.identity()));
    }

    /** Builds the provider's authorization URL (fresh single-use state included). */
    public String buildAuthorizationUrl(String provider) {
        OAuthProperties.Provider config = requireProvider(provider);
        String state = stateStore.issueState();
        return config.authorizationUri()
                + "?client_id="
                + encode(config.clientId())
                + "&redirect_uri="
                + encode(config.callbackUri())
                + "&response_type=code"
                + "&scope="
                + encode(config.scope())
                + "&state="
                + encode(state);
    }

    /**
     * Handles the provider callback end to end: validate state, exchange the code, fetch userinfo,
     * resolve/create the local user.
     */
    public UserEntity handleLoginCallback(String provider, String code, String state) {
        OAuthProperties.Provider config = requireProvider(provider);
        if (!stateStore.consumeState(state)) {
            throw new BadRequestException("Invalid or expired OAuth state");
        }
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Missing authorization code");
        }
        String accessToken = exchangeCodeForToken(provider, config, code);
        OAuthUserInfo userInfo =
                providers
                        .get(provider)
                        .fetchUserInfo(accessToken, restClient, config.userInfoUri());
        validateUserInfo(provider, userInfo);
        return userResolver.resolveLoginUser(provider, userInfo);
    }

    private String exchangeCodeForToken(
            String provider, OAuthProperties.Provider config, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", config.callbackUri());
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());

        Map<String, Object> response;
        try {
            response =
                    restClient
                            .post()
                            .uri(config.tokenUri())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(form)
                            .retrieve()
                            .body(TOKEN_RESPONSE_TYPE);
        } catch (RestClientException ex) {
            log.error("Token exchange request failed for provider {}", provider, ex);
            throw new BadGatewayException("Token exchange failed");
        }
        if (response == null || response.get("access_token") == null) {
            log.error("Token exchange returned no access_token for provider {}", provider);
            throw new BadGatewayException("Token exchange failed");
        }
        return String.valueOf(response.get("access_token"));
    }

    private void validateUserInfo(String provider, OAuthUserInfo userInfo) {
        if (userInfo == null || isBlank(userInfo.subject())) {
            throw new BadGatewayException(
                    "OAuth provider returned incomplete user information for " + provider);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private OAuthProperties.Provider requireProvider(String provider) {
        Map<String, OAuthProperties.Provider> configs = properties.providers();
        OAuthProperties.Provider config = configs == null ? null : configs.get(provider);
        if (config == null || !providers.containsKey(provider)) {
            throw new BadRequestException("Unknown OAuth provider: " + provider);
        }
        return config;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
