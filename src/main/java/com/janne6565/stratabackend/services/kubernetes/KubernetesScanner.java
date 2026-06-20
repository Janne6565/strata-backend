package com.janne6565.stratabackend.services.kubernetes;

import com.janne6565.stratabackend.model.core.WorkloadDescriptor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the cluster via fabric8 and produces a {@link WorkloadDescriptor} per Deployment/
 * StatefulSet (ARCHITECTURE.md §8). The backing Service is matched by selector ⊆ pod labels;
 * detector matching and credential resolution run downstream on the descriptors.
 */
@Component
@RequiredArgsConstructor
public class KubernetesScanner {

    private final KubernetesClient client;

    public List<WorkloadDescriptor> scan() {
        List<WorkloadDescriptor> descriptors = new ArrayList<>();
        for (Deployment d : client.apps().deployments().inAnyNamespace().list().getItems()) {
            describe("Deployment", d.getMetadata(), d.getSpec().getTemplate())
                    .ifPresent(descriptors::add);
        }
        for (StatefulSet s : client.apps().statefulSets().inAnyNamespace().list().getItems()) {
            describe("StatefulSet", s.getMetadata(), s.getSpec().getTemplate())
                    .ifPresent(descriptors::add);
        }
        return descriptors;
    }

    /**
     * Re-fetches a single workload's primary container — used to re-read inline (LITERAL)
     * credential values live at connection time (see {@code CredentialReader}).
     */
    public Optional<Container> primaryContainer(String namespace, String kind, String name) {
        PodTemplateSpec template =
                switch (kind == null ? "" : kind) {
                    case "Deployment" ->
                            templateOf(
                                    client.apps()
                                            .deployments()
                                            .inNamespace(namespace)
                                            .withName(name)
                                            .get());
                    case "StatefulSet" ->
                            templateOf(
                                    client.apps()
                                            .statefulSets()
                                            .inNamespace(namespace)
                                            .withName(name)
                                            .get());
                    default -> null;
                };
        return primaryContainer(template);
    }

    private static PodTemplateSpec templateOf(Deployment d) {
        return d == null || d.getSpec() == null ? null : d.getSpec().getTemplate();
    }

    private static PodTemplateSpec templateOf(StatefulSet s) {
        return s == null || s.getSpec() == null ? null : s.getSpec().getTemplate();
    }

    private Optional<Container> primaryContainer(PodTemplateSpec template) {
        if (template == null || template.getSpec() == null) {
            return Optional.empty();
        }
        List<Container> containers = template.getSpec().getContainers();
        if (containers == null || containers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(containers.get(0));
    }

    private Optional<WorkloadDescriptor> describe(
            String kind, ObjectMeta meta, PodTemplateSpec template) {
        Optional<Container> primaryOpt = primaryContainer(template);
        if (primaryOpt.isEmpty()) {
            return Optional.empty();
        }
        Container primary = primaryOpt.get();

        List<Integer> ports = new ArrayList<>(containerPorts(primary));
        Map<String, String> podLabels =
                template.getMetadata() == null ? null : template.getMetadata().getLabels();
        Service service = findBackingService(meta.getNamespace(), podLabels);
        Integer servicePort = service == null ? null : firstServicePort(service);
        if (servicePort != null && !ports.contains(servicePort)) {
            ports.add(servicePort);
        }

        return Optional.of(
                new WorkloadDescriptor(
                        meta.getNamespace(),
                        kind,
                        meta.getName(),
                        primary.getImage(),
                        ports,
                        primary,
                        service == null ? null : service.getMetadata().getName(),
                        servicePort));
    }

    private List<Integer> containerPorts(Container container) {
        if (container.getPorts() == null) {
            return List.of();
        }
        return container.getPorts().stream()
                .map(ContainerPort::getContainerPort)
                .filter(Objects::nonNull)
                .toList();
    }

    private Service findBackingService(String namespace, Map<String, String> podLabels) {
        if (podLabels == null || podLabels.isEmpty()) {
            return null;
        }
        return client.services().inNamespace(namespace).list().getItems().stream()
                .filter(s -> selectorMatches(s, podLabels))
                .findFirst()
                .orElse(null);
    }

    private boolean selectorMatches(Service service, Map<String, String> podLabels) {
        Map<String, String> selector =
                service.getSpec() == null ? null : service.getSpec().getSelector();
        return selector != null
                && !selector.isEmpty()
                && podLabels.entrySet().containsAll(selector.entrySet());
    }

    private Integer firstServicePort(Service service) {
        if (service.getSpec() == null
                || service.getSpec().getPorts() == null
                || service.getSpec().getPorts().isEmpty()) {
            return null;
        }
        return service.getSpec().getPorts().get(0).getPort();
    }
}
