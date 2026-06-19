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
import org.springframework.stereotype.Component;

/**
 * Reads the cluster via fabric8 and produces a {@link WorkloadDescriptor} per Deployment/
 * StatefulSet (ARCHITECTURE.md §8). The backing Service is matched by selector ⊆ pod labels;
 * detector matching and credential resolution run downstream on the descriptors.
 */
@Component
public class KubernetesScanner {

    private final KubernetesClient client;

    public KubernetesScanner(KubernetesClient client) {
        this.client = client;
    }

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

    private Optional<WorkloadDescriptor> describe(
            String kind, ObjectMeta meta, PodTemplateSpec template) {
        if (template == null || template.getSpec() == null) {
            return Optional.empty();
        }
        List<Container> containers = template.getSpec().getContainers();
        if (containers == null || containers.isEmpty()) {
            return Optional.empty();
        }
        Container primary = containers.get(0);

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
