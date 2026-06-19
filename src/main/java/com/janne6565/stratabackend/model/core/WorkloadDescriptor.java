package com.janne6565.stratabackend.model.core;

import io.fabric8.kubernetes.api.model.Container;
import java.util.List;

/**
 * What a scan extracts about a candidate database workload: its identity, primary container image
 * and observed ports, the container itself (for credential resolution), and its backing Service.
 * The {@link #discoveryKey()} is the stable natural key used to reconcile scans against the catalog.
 */
public record WorkloadDescriptor(
        String namespace,
        String workloadKind,
        String workloadName,
        String image,
        List<Integer> ports,
        Container primaryContainer,
        String serviceName,
        Integer servicePort) {

    public String discoveryKey() {
        return namespace + "/" + workloadKind + "/" + workloadName;
    }
}
