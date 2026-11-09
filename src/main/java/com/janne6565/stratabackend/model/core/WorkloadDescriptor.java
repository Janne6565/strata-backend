package com.janne6565.stratabackend.model.core;

import io.fabric8.kubernetes.api.model.Container;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * What a scan extracts about a candidate database workload: its identity, primary container image
 * and observed ports, the container itself (for credential resolution), and its backing Service.
 * {@code serviceName}/{@code servicePort} are the default endpoint (first matching Service, first
 * port); {@code serviceEndpoints} lists every port of every Service selecting the workload, so a
 * detector can pick its protocol port (e.g. MinIO's S3 API on 9000 over its console on 9001). The
 * {@link #discoveryKey()} is the stable natural key used to reconcile scans against the catalog.
 */
public record WorkloadDescriptor(
        String namespace,
        String workloadKind,
        String workloadName,
        String image,
        List<Integer> ports,
        Container primaryContainer,
        String serviceName,
        Integer servicePort,
        List<ServiceEndpoint> serviceEndpoints) {

    /** One port of a Service that selects the workload. */
    public record ServiceEndpoint(String serviceName, Integer port) {}

    public WorkloadDescriptor {
        serviceEndpoints = serviceEndpoints == null ? List.of() : List.copyOf(serviceEndpoints);
    }

    /** A workload whose only known endpoint is {@code serviceName:servicePort}. */
    public WorkloadDescriptor(
            String namespace,
            String workloadKind,
            String workloadName,
            String image,
            List<Integer> ports,
            Container primaryContainer,
            String serviceName,
            Integer servicePort) {
        this(
                namespace,
                workloadKind,
                workloadName,
                image,
                ports,
                primaryContainer,
                serviceName,
                servicePort,
                serviceName == null || servicePort == null
                        ? List.of()
                        : List.of(new ServiceEndpoint(serviceName, servicePort)));
    }

    public String discoveryKey() {
        return namespace + "/" + workloadKind + "/" + workloadName;
    }

    /**
     * The first Service endpoint whose port is one of {@code preferredPorts}; empty when none
     * matches (callers then keep the default {@code serviceName}/{@code servicePort}).
     */
    public Optional<ServiceEndpoint> preferredEndpoint(Collection<Integer> preferredPorts) {
        if (preferredPorts == null || preferredPorts.isEmpty()) {
            return Optional.empty();
        }
        return serviceEndpoints.stream()
                .filter(endpoint -> preferredPorts.contains(endpoint.port()))
                .findFirst();
    }
}
