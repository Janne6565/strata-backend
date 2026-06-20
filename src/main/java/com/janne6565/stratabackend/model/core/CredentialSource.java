package com.janne6565.stratabackend.model.core;

/**
 * How a single credential field resolves. For SECRET/CONFIG_MAP, {@code name}/{@code key} point at
 * the backing Secret/ConfigMap. For LITERAL, {@code key} holds the container env var name so the
 * inline value can be re-read live from the workload's pod spec at connection time (a pointer only
 * — the value itself is never stored here). Both are null for UNRESOLVED. (AUTH.md)
 */
public record CredentialSource(String field, CredentialSourceType type, String name, String key) {

    public static CredentialSource literal(String field, String envVar) {
        return new CredentialSource(field, CredentialSourceType.LITERAL, null, envVar);
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
