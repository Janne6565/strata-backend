package com.janne6565.stratabackend.services.discovery;

import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.Detector;
import com.janne6565.stratabackend.model.core.Confidence;
import com.janne6565.stratabackend.model.core.DetectorMatch;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Matches a workload's container image against the configured detectors and scores the confidence
 * (ARCHITECTURE.md §8). Regexes are compiled once at startup so a malformed detector pattern fails
 * fast rather than per request. When several detectors match, the highest-confidence one wins (ties
 * broken by config order).
 */
@Component
public class DetectorMatcher {

    private final List<CompiledDetector> detectors;

    public DetectorMatcher(DiscoveryProperties properties) {
        this.detectors =
                properties.detectors().stream()
                        .filter(d -> d.match() != null && d.match().ref() != null)
                        .map(CompiledDetector::compile)
                        .toList();
    }

    /**
     * @param imageRef the full {@code repository:tag} reference of the workload's primary container
     * @param observedPorts container/service ports observed for the workload
     * @return the best detector match, or empty if no detector's image regex matches
     */
    public Optional<DetectorMatch> match(String imageRef, Collection<Integer> observedPorts) {
        if (imageRef == null) {
            return Optional.empty();
        }
        Set<Integer> ports = Set.copyOf(observedPorts);
        return detectors.stream()
                .filter(d -> d.pattern().matcher(imageRef).find())
                .map(d -> new DetectorMatch(d.id(), d.driver(), score(d, ports)))
                .max((a, b) -> Integer.compare(a.confidence().ordinal(), b.confidence().ordinal()));
    }

    private Confidence score(CompiledDetector detector, Set<Integer> observedPorts) {
        boolean portCorroborated =
                !detector.expectedPorts().isEmpty()
                        && detector.expectedPorts().stream().anyMatch(observedPorts::contains);
        return portCorroborated ? Confidence.HIGH : Confidence.MEDIUM;
    }

    private record CompiledDetector(
            String id, String driver, Pattern pattern, List<Integer> expectedPorts) {

        static CompiledDetector compile(Detector detector) {
            try {
                List<Integer> ports =
                        detector.match().ports() == null ? List.of() : detector.match().ports();
                return new CompiledDetector(
                        detector.id(),
                        detector.driver(),
                        Pattern.compile(detector.match().ref()),
                        ports);
            } catch (PatternSyntaxException ex) {
                throw new IllegalStateException(
                        "Invalid image regex in detector '"
                                + detector.id()
                                + "': "
                                + ex.getMessage(),
                        ex);
            }
        }
    }
}
