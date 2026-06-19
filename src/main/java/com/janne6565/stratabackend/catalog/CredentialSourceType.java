package com.janne6565.stratabackend.catalog;

/** Where a credential field's value comes from in the workload's environment. */
public enum CredentialSourceType {
    /** An inline {@code env[].value}. The value is the credential, so it is never persisted. */
    LITERAL,
    /** A {@code secretKeyRef} or an {@code envFrom.secretRef} (name + key recorded, value live). */
    SECRET,
    /** A {@code configMapKeyRef} or an {@code envFrom.configMapRef}. */
    CONFIG_MAP,
    /** The field's env var was not found in the container's env/envFrom — needs manual entry. */
    UNRESOLVED
}
