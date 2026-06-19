package com.janne6565.stratabackend.catalog;

/**
 * How a single credential field resolves. {@code name}/{@code key} point at the backing
 * Secret/ConfigMap (null for LITERAL/UNRESOLVED). The actual value is read live and never stored
 * here (AUTH.md).
 */
public record CredentialSource(
        String field, CredentialSourceType type, String name, String key) {

    public static CredentialSource literal(String field) {
        return new CredentialSource(field, CredentialSourceType.LITERAL, null, null);
    }

    public static CredentialSource secret(String field, String name, String key) {
        return new CredentialSource(field, CredentialSourceType.SECRET, name, key);
    }

    public static CredentialSource configMap(String field, String name, String key) {
        return new CredentialSource(field, CredentialSourceType.CONFIG_MAP, name, key);
    }

    public static CredentialSource unresolved(String field) {
        return new CredentialSource(field, CredentialSourceType.UNRESOLVED, null, null);
    }
}
