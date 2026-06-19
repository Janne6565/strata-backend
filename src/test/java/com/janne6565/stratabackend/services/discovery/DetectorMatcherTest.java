package com.janne6565.stratabackend.services.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.Detector;
import com.janne6565.stratabackend.configuration.discovery.DiscoveryProperties.Match;
import com.janne6565.stratabackend.model.core.Confidence;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DetectorMatcherTest {

    private DetectorMatcher matcherWith(Detector... detectors) {
        return new DetectorMatcher(new DiscoveryProperties(List.of(detectors)));
    }

    private Detector detector(String id, String driver, String ref, List<Integer> ports) {
        return new Detector(id, driver, new Match(ref, ports), null);
    }

    @Test
    void noMatchWhenImageDoesNotMatchAnyDetector() {
        DetectorMatcher matcher =
                matcherWith(detector("pg", "postgresql", "postgres", List.of(5432)));

        assertThat(matcher.match("docker.io/library/redis:7", Set.of())).isEmpty();
    }

    @Test
    void imageMatchAloneIsMedium() {
        DetectorMatcher matcher =
                matcherWith(detector("pg", "postgresql", "postgres", List.of(5432)));

        var match = matcher.match("docker.io/library/postgres:17-alpine", Set.of());

        assertThat(match).isPresent();
        assertThat(match.get().driver()).isEqualTo("postgresql");
        assertThat(match.get().confidence()).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void portCorroborationRaisesToHigh() {
        DetectorMatcher matcher =
                matcherWith(detector("pg", "postgresql", "postgres", List.of(5432)));

        var match = matcher.match("postgres:17", Set.of(5432));

        assertThat(match).isPresent();
        assertThat(match.get().confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void highestConfidenceWinsWhenMultipleMatch() {
        DetectorMatcher matcher =
                matcherWith(
                        detector("broad", "generic", "postgres", List.of()),
                        detector("precise", "postgresql", "postgres", List.of(5432)));

        var match = matcher.match("postgres:17", Set.of(5432));

        assertThat(match).isPresent();
        assertThat(match.get().confidence()).isEqualTo(Confidence.HIGH);
        assertThat(match.get().driver()).isEqualTo("postgresql");
    }

    @Test
    void detectorsWithoutAMatchRefAreIgnored() {
        DetectorMatcher matcher =
                matcherWith(new Detector("broken", "x", new Match(null, null), null));

        assertThat(matcher.match("anything:latest", Set.of())).isEmpty();
    }
}
