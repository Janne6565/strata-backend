package com.janne6565.stratabackend.services.auth.oauth;

import org.springframework.web.client.RestClient;

/**
 * Strategy for a single OAuth/OIDC provider. Implementations map the provider's userinfo response
 * onto the normalised {@link OAuthUserInfo}. The shared {@link RestClient} is passed in so the
 * {@link OAuthService} owns HTTP configuration centrally.
 */
public interface OAuthProviderClient {

    /** The provider key used in routes/config (e.g. {@code authentik}). */
    String providerName();

    OAuthUserInfo fetchUserInfo(String accessToken, RestClient restClient, String userInfoUri);
}
