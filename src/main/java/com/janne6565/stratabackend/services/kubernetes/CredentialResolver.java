package com.janne6565.stratabackend.services.kubernetes;

import com.janne6565.stratabackend.model.core.CredentialResolution;
import com.janne6565.stratabackend.model.core.CredentialSource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Walks a container's environment to determine where each credential field comes from, in
 * Kubernetes precedence (ARCHITECTURE.md §8): {@code env[].value} ▸ {@code env[].valueFrom}
 * (secret/configmap key) ▸ {@code envFrom[]} (secret/configmap, honouring {@code prefix}).
 * Produces a {@link CredentialResolution} of pointers only — actual secret values are read live
 * at connection time, never here (AUTH.md).
 */
@Component
public class CredentialResolver {

    /**
     * @param container the workload's primary container
     * @param fieldToEnvVar detector mapping of credential field → environment variable name
     * @param strategy a label for how resolution was derived (e.g. the detector id)
     */
    public CredentialResolution resolve(
            Container container, Map<String, String> fieldToEnvVar, String strategy) {
        List<EnvVar> env = container.getEnv() == null ? List.of() : container.getEnv();
        List<EnvFromSource> envFrom =
                container.getEnvFrom() == null ? List.of() : container.getEnvFrom();

        List<CredentialSource> sources = new ArrayList<>();
        fieldToEnvVar.forEach(
                (field, envVarName) ->
                        sources.add(resolveField(field, envVarName, env, envFrom)));
        return new CredentialResolution(strategy, sources);
    }

    private CredentialSource resolveField(
            String field, String envVarName, List<EnvVar> env, List<EnvFromSource> envFrom) {
        for (EnvVar var : env) {
            if (envVarName.equals(var.getName())) {
                return fromEnvVar(field, var);
            }
        }
        return fromEnvFrom(field, envVarName, envFrom);
    }

    private CredentialSource fromEnvVar(String field, EnvVar var) {
        if (var.getValue() != null) {
            return CredentialSource.literal(field);
        }
        EnvVarSource source = var.getValueFrom();
        if (source != null && source.getSecretKeyRef() != null) {
            return CredentialSource.secret(
                    field, source.getSecretKeyRef().getName(), source.getSecretKeyRef().getKey());
        }
        if (source != null && source.getConfigMapKeyRef() != null) {
            return CredentialSource.configMap(
                    field,
                    source.getConfigMapKeyRef().getName(),
                    source.getConfigMapKeyRef().getKey());
        }
        return CredentialSource.unresolved(field);
    }

    private CredentialSource fromEnvFrom(
            String field, String envVarName, List<EnvFromSource> envFrom) {
        for (EnvFromSource source : envFrom) {
            String prefix = source.getPrefix() == null ? "" : source.getPrefix();
            if (!envVarName.startsWith(prefix)) {
                continue;
            }
            String key = envVarName.substring(prefix.length());
            if (source.getSecretRef() != null) {
                return CredentialSource.secret(field, source.getSecretRef().getName(), key);
            }
            if (source.getConfigMapRef() != null) {
                return CredentialSource.configMap(field, source.getConfigMapRef().getName(), key);
            }
        }
        return CredentialSource.unresolved(field);
    }
}
