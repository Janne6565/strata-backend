package com.janne6565.stratabackend.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test: scans a real (k3s) cluster. Applies a postgres Deployment + Service and asserts
 * the {@link KubernetesScanner} surfaces it as a {@link WorkloadDescriptor} with the right image,
 * port and backing service. Requires Docker.
 */
@Testcontainers
class KubernetesScannerK3sTest {

    @Container
    static final K3sContainer K3S =
            new K3sContainer(DockerImageName.parse("rancher/k3s:v1.33.12-k3s1"));

    static KubernetesClient client;

    @BeforeAll
    static void connect() {
        client =
                new KubernetesClientBuilder()
                        .withConfig(Config.fromKubeconfig(K3S.getKubeConfigYaml()))
                        .build();
    }

    @AfterAll
    static void close() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void discoversAPostgresDeploymentWithItsService() {
        Deployment deployment =
                new DeploymentBuilder()
                        .withNewMetadata()
                        .withName("orders")
                        .withNamespace("default")
                        .endMetadata()
                        .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector()
                        .addToMatchLabels("app", "orders")
                        .endSelector()
                        .withNewTemplate()
                        .withNewMetadata()
                        .addToLabels("app", "orders")
                        .endMetadata()
                        .withNewSpec()
                        .addNewContainer()
                        .withName("db")
                        .withImage("postgres:17-alpine")
                        .addNewPort()
                        .withContainerPort(5432)
                        .endPort()
                        .addNewEnv()
                        .withName("PGPASSWORD")
                        .withNewValueFrom()
                        .withNewSecretKeyRef("password", "pg-secret", false)
                        .endValueFrom()
                        .endEnv()
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build();
        client.apps().deployments().inNamespace("default").resource(deployment).create();

        Service service =
                new ServiceBuilder()
                        .withNewMetadata()
                        .withName("orders")
                        .withNamespace("default")
                        .endMetadata()
                        .withNewSpec()
                        .addToSelector("app", "orders")
                        .addNewPort()
                        .withPort(5432)
                        .withNewTargetPort(5432)
                        .endPort()
                        .endSpec()
                        .build();
        client.services().inNamespace("default").resource(service).create();

        List<WorkloadDescriptor> workloads = new KubernetesScanner(client).scan();

        WorkloadDescriptor orders =
                workloads.stream()
                        .filter(w -> "orders".equals(w.workloadName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(orders.image()).startsWith("postgres");
        assertThat(orders.workloadKind()).isEqualTo("Deployment");
        assertThat(orders.ports()).contains(5432);
        assertThat(orders.serviceName()).isEqualTo("orders");
        assertThat(orders.servicePort()).isEqualTo(5432);
        assertThat(orders.primaryContainer().getEnv()).isNotEmpty();
    }
}
