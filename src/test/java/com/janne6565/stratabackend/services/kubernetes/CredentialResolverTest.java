package com.janne6565.stratabackend.services.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import com.janne6565.stratabackend.model.core.CredentialResolution;
import com.janne6565.stratabackend.model.core.CredentialSource;
import com.janne6565.stratabackend.model.core.CredentialSourceType;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialResolverTest {

    private final CredentialResolver resolver = new CredentialResolver();

    private CredentialSource sourceFor(CredentialResolution resolution, String field) {
        return resolution.sources().stream()
                .filter(s -> s.field().equals(field))
                .findFirst()
                .orElseThrow();
    }

    private Container container(
            java.util.List<EnvVar> env, io.fabric8.kubernetes.api.model.EnvFromSource... envFrom) {
        return new ContainerBuilder().withName("db").withEnv(env).withEnvFrom(envFrom).build();
    }

    @Test
    void resolvesLiteralSecretKeyRefAndConfigMapKeyRef() {
        EnvVar literal = new EnvVarBuilder().withName("PGUSER").withValue("admin").build();
        EnvVar secretRef =
                new EnvVarBuilder()
                        .withName("PGPASSWORD")
                        .withNewValueFrom()
                        .withNewSecretKeyRef("password", "pg-secret", false)
                        .endValueFrom()
                        .build();
        EnvVar configRef =
                new EnvVarBuilder()
                        .withName("PGDATABASE")
                        .withNewValueFrom()
                        .withNewConfigMapKeyRef("db-name", "pg-config", false)
                        .endValueFrom()
                        .build();

        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("username", "PGUSER");
        mapping.put("password", "PGPASSWORD");
        mapping.put("database", "PGDATABASE");

        CredentialResolution resolution =
                resolver.resolve(
                        container(java.util.List.of(literal, secretRef, configRef)), mapping, "pg");

        CredentialSource username = sourceFor(resolution, "username");
        assertThat(username.type()).isEqualTo(CredentialSourceType.LITERAL);
        // The env var name is recorded so the inline value can be re-read live at connect time.
        assertThat(username.key()).isEqualTo("PGUSER");

        CredentialSource password = sourceFor(resolution, "password");
        assertThat(password.type()).isEqualTo(CredentialSourceType.SECRET);
        assertThat(password.name()).isEqualTo("pg-secret");
        assertThat(password.key()).isEqualTo("password");

        CredentialSource database = sourceFor(resolution, "database");
        assertThat(database.type()).isEqualTo(CredentialSourceType.CONFIG_MAP);
        assertThat(database.name()).isEqualTo("pg-config");
        assertThat(database.key()).isEqualTo("db-name");

        assertThat(resolution.allResolved()).isTrue();
    }

    @Test
    void resolvesViaEnvFromSecretHonouringPrefix() {
        var envFrom =
                new EnvFromSourceBuilder()
                        .withPrefix("DB_")
                        .withNewSecretRef("app-secret", false)
                        .build();

        CredentialResolution resolution =
                resolver.resolve(
                        container(java.util.List.of(), envFrom),
                        Map.of("password", "DB_PASSWORD"),
                        "pg");

        CredentialSource password = sourceFor(resolution, "password");
        assertThat(password.type()).isEqualTo(CredentialSourceType.SECRET);
        assertThat(password.name()).isEqualTo("app-secret");
        assertThat(password.key()).isEqualTo("PASSWORD"); // prefix stripped
    }

    @Test
    void marksUnresolvedWhenEnvVarAbsent() {
        CredentialResolution resolution =
                resolver.resolve(
                        container(java.util.List.of()), Map.of("password", "PGPASSWORD"), "pg");

        assertThat(sourceFor(resolution, "password").type())
                .isEqualTo(CredentialSourceType.UNRESOLVED);
        assertThat(resolution.allResolved()).isFalse();
    }
}
