package com.janne6565.stratabackend.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the fabric8 {@link KubernetesClient}. In-cluster it authenticates via the mounted
 * ServiceAccount; locally it falls back to the kubeconfig. Construction is lazy (no connection at
 * startup), so the bean is safe to create even when no cluster is reachable.
 */
@Configuration
public class KubernetesClientConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
