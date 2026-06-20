package com.janne6565.stratabackend.services.kubernetes;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.CredentialResolution;
import com.janne6565.stratabackend.model.core.CredentialSource;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.services.engine.ConnectionDetailsResolver;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves a datasource's connection credentials on demand by reading the backing Secret/ConfigMap
 * values from the cluster (AUTH.md: in-memory only, never persisted). Builds the cluster-DNS host
 * from the datasource's Service. Inline (LITERAL) fields are re-read live from the workload's pod
 * spec by env var name; only fields with no recoverable source (UNRESOLVED, or a literal whose env
 * var is gone) must be supplied via a manual override.
 */
@Component
@RequiredArgsConstructor
public class CredentialReader implements ConnectionDetailsResolver {

    private final KubernetesClient client;
    private final KubernetesScanner scanner;

    @Override
    public ConnectionDetails resolve(DatasourceEntity datasource) {
        if (datasource.getServiceName() == null || datasource.getServicePort() == null) {
            throw new BadRequestException(
                    "DatasourceEntity has no backing service; cannot determine host/port");
        }
        String host =
                datasource.getServiceName()
                        + "."
                        + datasource.getNamespace()
                        + ".svc.cluster.local";

        Map<String, String> values = resolveValues(datasource);
        String username = values.get("username");
        String database = values.getOrDefault("database", username);
        return new ConnectionDetails(
                datasource.getDriver(),
                host,
                datasource.getServicePort(),
                database,
                username,
                values.get("password"));
    }

    private Map<String, String> resolveValues(DatasourceEntity datasource) {
        CredentialResolution resolution = datasource.getCredentialResolution();
        if (resolution == null) {
            throw new BadRequestException("DatasourceEntity has no resolved credentials");
        }
        Map<String, String> values = new HashMap<>();
        for (CredentialSource source : resolution.sources()) {
            values.put(source.field(), readValue(datasource, source));
        }
        return values;
    }

    private String readValue(DatasourceEntity datasource, CredentialSource source) {
        String namespace = datasource.getNamespace();
        return switch (source.type()) {
            case SECRET -> decode(readSecretValue(namespace, source.name(), source.key()));
            case CONFIG_MAP -> readConfigMapValue(namespace, source.name(), source.key());
            case LITERAL -> readLiteralValue(datasource, source);
            case UNRESOLVED -> throw needsManualOverride(source);
        };
    }

    /**
     * Re-reads an inline {@code env[].value} live from the workload's primary container (the value
     * was never persisted — only the env var name in {@code source.key()}). Falls back to requiring
     * a manual override if the env var is gone or no longer inline (e.g. moved to a secretKeyRef).
     */
    private String readLiteralValue(DatasourceEntity datasource, CredentialSource source) {
        if (source.key() == null) {
            // Resolution recorded before literals carried the env var name.
            throw needsManualOverride(source);
        }
        Container container =
                scanner.primaryContainer(
                                datasource.getNamespace(),
                                datasource.getWorkloadKind(),
                                datasource.getWorkloadName())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Workload "
                                                        + datasource.getNamespace()
                                                        + "/"
                                                        + datasource.getWorkloadKind()
                                                        + "/"
                                                        + datasource.getWorkloadName()));
        List<EnvVar> env = container.getEnv() == null ? List.of() : container.getEnv();
        return env.stream()
                .filter(e -> source.key().equals(e.getName()))
                .map(EnvVar::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> needsManualOverride(source));
    }

    private BadRequestException needsManualOverride(CredentialSource source) {
        return new BadRequestException(
                "Credential field '"
                        + source.field()
                        + "' is "
                        + source.type()
                        + " and must be supplied via a manual override");
    }

    private String readSecretValue(String namespace, String name, String key) {
        Secret secret = client.secrets().inNamespace(namespace).withName(name).get();
        if (secret == null || secret.getData() == null || !secret.getData().containsKey(key)) {
            throw new NotFoundException("Secret " + namespace + "/" + name + " key '" + key + "'");
        }
        return secret.getData().get(key);
    }

    private String readConfigMapValue(String namespace, String name, String key) {
        ConfigMap configMap = client.configMaps().inNamespace(namespace).withName(name).get();
        if (configMap == null
                || configMap.getData() == null
                || !configMap.getData().containsKey(key)) {
            throw new NotFoundException(
                    "ConfigMap " + namespace + "/" + name + " key '" + key + "'");
        }
        return configMap.getData().get(key);
    }

    private String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}
