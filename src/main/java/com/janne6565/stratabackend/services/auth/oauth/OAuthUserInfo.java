package com.janne6565.stratabackend.services.auth.oauth;

import java.util.List;

/**
 * The provider's view of the logging-in user, normalised across providers. {@code groups} may be
 * empty when the provider omits them; the strict group gate then rejects the login.
 */
public record OAuthUserInfo(String subject, String email, String username, List<String> groups) {}
