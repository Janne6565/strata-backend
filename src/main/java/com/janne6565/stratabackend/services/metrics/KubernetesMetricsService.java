package com.janne6565.stratabackend.services.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Best-effort pod telemetry for a datasource's backing workload (the Kubernetes side of {@code
 * ResourceMetricsResponse}). Replica counts come from the Deployment/StatefulSet status; CPU/memory
 * percentages are live usage (metrics-server, via fabric8 {@code top()}) over the pod-spec limits.
 * Everything degrades gracefully: no backing workload, no metrics-server, or no resource limits
 * each yield {@link Optional#empty()} or null sub-metrics rather than an error.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesMetricsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KubernetesClient client;

    /** The Kubernetes-sourced sub-metrics; any field may be null when its source is unavailable. */
    public record PodMetricsSummary(
            Double cpuPercent,
            Double memoryPercent,
            Long memoryUsageBytes,
            Long volumeUsedBytes,
            Integer podsReady,
            Integer podsDesired) {}

    public Optional<PodMetricsSummary> sample(DatasourceEntity datasource) {
        String namespace = datasource.getNamespace();
        String kind = datasource.getWorkloadKind();
        String name = datasource.getWorkloadName();
        if (namespace == null || kind == null || name == null) {
            return Optional.empty();
        }
        try {
            Workload workload = loadWorkload(namespace, kind, name);
            return workload == null
                    ? Optional.empty()
                    : Optional.of(summarise(namespace, workload));
        } catch (RuntimeException ex) {
            log.debug(
                    "Kubernetes metrics unavailable for {}/{}: {}",
                    namespace,
                    name,
                    ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Per-pod limits + replica status for one workload (limits are 0 when unset → percent null).
     */
    private record Workload(
            Map<String, String> selector,
            double cpuLimitCores,
            double memLimitBytes,
            Integer podsReady,
            Integer podsDesired) {}

    private Workload loadWorkload(String namespace, String kind, String name) {
        if ("StatefulSet".equalsIgnoreCase(kind)) {
            StatefulSet s =
                    client.apps().statefulSets().inNamespace(namespace).withName(name).get();
            if (s == null || s.getSpec() == null) {
                return null;
            }
            Integer ready = s.getStatus() == null ? null : s.getStatus().getReadyReplicas();
            return build(
                    s.getSpec().getSelector() == null
                            ? null
                            : s.getSpec().getSelector().getMatchLabels(),
                    s.getSpec().getTemplate(),
                    ready,
                    s.getSpec().getReplicas());
        }
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null || d.getSpec() == null) {
            return null;
        }
        Integer ready = d.getStatus() == null ? null : d.getStatus().getReadyReplicas();
        return build(
                d.getSpec().getSelector() == null
                        ? null
                        : d.getSpec().getSelector().getMatchLabels(),
                d.getSpec().getTemplate(),
                ready,
                d.getSpec().getReplicas());
    }

    private Workload build(
            Map<String, String> selector,
            PodTemplateSpec template,
            Integer podsReady,
            Integer podsDesired) {
        double cpuLimit = 0;
        double memLimit = 0;
        if (template != null
                && template.getSpec() != null
                && template.getSpec().getContainers() != null) {
            for (Container container : template.getSpec().getContainers()) {
                Map<String, Quantity> limits =
                        container.getResources() == null
                                ? null
                                : container.getResources().getLimits();
                if (limits != null) {
                    cpuLimit += amount(limits.get("cpu"));
                    memLimit += amount(limits.get("memory"));
                }
            }
        }
        return new Workload(selector, cpuLimit, memLimit, podsReady, podsDesired);
    }

    private PodMetricsSummary summarise(String namespace, Workload workload) {
        Double cpuPercent = null;
        Double memoryPercent = null;
        Long memoryUsageBytes = null;
        if (workload.selector() != null && !workload.selector().isEmpty()) {
            try {
                List<PodMetrics> pods =
                        client.top()
                                .pods()
                                .inNamespace(namespace)
                                .withLabels(workload.selector())
                                .metrics()
                                .getItems();
                if (pods != null && !pods.isEmpty()) {
                    double cpuUsage = 0;
                    double memUsage = 0;
                    for (PodMetrics pod : pods) {
                        if (pod.getContainers() == null) {
                            continue;
                        }
                        for (ContainerMetrics container : pod.getContainers()) {
                            Map<String, Quantity> usage = container.getUsage();
                            if (usage != null) {
                                cpuUsage += amount(usage.get("cpu"));
                                memUsage += amount(usage.get("memory"));
                            }
                        }
                    }
                    memoryUsageBytes = (long) memUsage;
                    int podCount = pods.size();
                    double cpuLimitTotal = workload.cpuLimitCores() * podCount;
                    double memLimitTotal = workload.memLimitBytes() * podCount;
                    if (cpuLimitTotal > 0) {
                        cpuPercent = round(cpuUsage / cpuLimitTotal * 100);
                    }
                    if (memLimitTotal > 0) {
                        memoryPercent = round(memUsage / memLimitTotal * 100);
                    }
                }
            } catch (RuntimeException ex) {
                // No metrics-server, or it rejected the request — keep replica counts, drop usage.
                log.debug("metrics-server unavailable in {}: {}", namespace, ex.getMessage());
            }
        }
        Long volumeUsedBytes = sampleVolumeUsedBytes(namespace, workload.selector());
        return new PodMetricsSummary(
                cpuPercent,
                memoryPercent,
                memoryUsageBytes,
                volumeUsedBytes,
                workload.podsReady(),
                workload.podsDesired());
    }

    /**
     * On-disk size of the workload's PVC-backed volumes, summed across its pods. Sourced from the
     * kubelet Summary API ({@code /nodes/<node>/proxy/stats/summary}) — the only place that reports
     * live volume <em>usage</em> (metrics-server and the PVC status only expose capacity). Used as
     * the {@code dataSizeBytes} fallback for engines that can't report their own size (e.g. Loki,
     * InfluxDB). Best-effort: any failure (no {@code nodes/proxy} permission, kubelet unreachable)
     * yields null.
     */
    private Long sampleVolumeUsedBytes(String namespace, Map<String, String> selector) {
        if (selector == null || selector.isEmpty()) {
            return null;
        }
        try {
            List<Pod> pods =
                    client.pods().inNamespace(namespace).withLabels(selector).list().getItems();
            if (pods == null || pods.isEmpty()) {
                return null;
            }
            Map<String, Set<String>> podNamesByNode = new HashMap<>();
            for (Pod pod : pods) {
                String node = pod.getSpec() == null ? null : pod.getSpec().getNodeName();
                if (node == null || pod.getMetadata() == null) {
                    continue;
                }
                podNamesByNode
                        .computeIfAbsent(node, k -> new HashSet<>())
                        .add(pod.getMetadata().getName());
            }
            long total = 0;
            boolean found = false;
            for (Map.Entry<String, Set<String>> entry : podNamesByNode.entrySet()) {
                Long bytes = volumeUsedBytesOnNode(entry.getKey(), namespace, entry.getValue());
                if (bytes != null) {
                    total += bytes;
                    found = true;
                }
            }
            return found ? total : null;
        } catch (RuntimeException ex) {
            log.debug("PVC volume stats unavailable in {}: {}", namespace, ex.getMessage());
            return null;
        }
    }

    private Long volumeUsedBytesOnNode(String node, String namespace, Set<String> podNames) {
        try {
            String body = client.raw("/api/v1/nodes/" + node + "/proxy/stats/summary");
            if (body == null || body.isBlank()) {
                return null;
            }
            return sumPvcUsedBytes(MAPPER.readTree(body), namespace, podNames);
        } catch (Exception ex) {
            log.debug("kubelet volume stats unavailable on node {}: {}", node, ex.getMessage());
            return null;
        }
    }

    /**
     * Sums {@code usedBytes} across PVC-backed volumes of the named pods in a kubelet Summary
     * document. Non-PVC volumes (emptyDir, configmaps, …) are excluded so the figure reflects
     * persisted data.
     *
     * <p>Volumes whose {@code capacityBytes} equals the node's root filesystem capacity are
     * skipped: that is the signature of hostPath / {@code local-path} storage, where the kubelet
     * reports the whole node disk for every PVC rather than the volume's own data — a misleading
     * figure. Only dedicated volumes (CSI / block) are counted. Returns null when no such volume is
     * present (which is the common case on local-path clusters, surfaced as "—" rather than a wrong
     * size).
     */
    static Long sumPvcUsedBytes(JsonNode summary, String namespace, Set<String> podNames) {
        long nodeFsCapacity = summary.path("node").path("fs").path("capacityBytes").asLong(-1);
        long total = 0;
        boolean found = false;
        for (JsonNode pod : summary.path("pods")) {
            JsonNode ref = pod.path("podRef");
            if (!namespace.equals(ref.path("namespace").asText())
                    || !podNames.contains(ref.path("name").asText())) {
                continue;
            }
            for (JsonNode volume : pod.path("volume")) {
                if (volume.path("pvcRef").isMissingNode()) {
                    continue;
                }
                long capacity = volume.path("capacityBytes").asLong(-1);
                if (capacity > 0 && capacity == nodeFsCapacity) {
                    continue; // hostPath/local-path: reflects the node disk, not the volume
                }
                JsonNode used = volume.path("usedBytes");
                if (used.isNumber()) {
                    total += used.asLong();
                    found = true;
                }
            }
        }
        return found ? total : null;
    }

    /** Resolves a {@link Quantity} to its base-unit amount: cores for CPU, bytes for memory. */
    private double amount(Quantity quantity) {
        if (quantity == null) {
            return 0;
        }
        try {
            return Quantity.getAmountInBytes(quantity).doubleValue();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
