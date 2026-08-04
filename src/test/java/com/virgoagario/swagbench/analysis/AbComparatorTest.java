package com.virgoagario.swagbench.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbComparatorTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsCandidateBetterWhenBootstrapMedianCiIsBelowZero() throws Exception {
        Path baseline = tempDir.resolve("baseline");
        Path candidate = tempDir.resolve("candidate");
        Files.createDirectories(baseline);
        Files.createDirectories(candidate);
        writeReport(baseline.resolve("b1.json"), 20_000_000L, 25_000_000L, 1_000L);
        writeReport(baseline.resolve("b2.json"), 22_000_000L, 27_000_000L, 1_200L);
        writeReport(candidate.resolve("c1.json"), 14_000_000L, 17_000_000L, 800L);
        writeReport(candidate.resolve("c2.json"), 15_000_000L, 18_000_000L, 900L);

        AbComparisonResult result = new AbComparator().compare(baseline, candidate);

        assertEquals("better", result.metric("tick.p95Nanos").verdict());
        assertTrue(result.metric("tick.p95Nanos").candidateMedian() < result.metric("tick.p95Nanos").baselineMedian());
        assertTrue(result.toText().contains("tick.p95Nanos"));
        assertTrue(result.toText().contains("ciLow"));
    }

    @Test
    void rejectsReportSetsWithMissingMetrics() throws Exception {
        Path baseline = tempDir.resolve("baseline-missing");
        Path candidate = tempDir.resolve("candidate-missing");
        Files.createDirectories(baseline);
        Files.createDirectories(candidate);
        Files.writeString(baseline.resolve("b1.json"), "{\"mode\":\"realistic\",\"tick\":{\"clean\":{\"p95Nanos\":1}}}");
        writeReport(candidate.resolve("c1.json"), 14_000_000L, 17_000_000L, 800L);

        assertThrows(IllegalArgumentException.class, () -> new AbComparator().compare(baseline, candidate));
    }

    private static void writeReport(Path path, long medianNanos, long p95Nanos, long bytesIn) throws Exception {
        Files.writeString(path, """
                {
                  "mode": "realistic",
                  "tick": {"clean": {"medianNanos": %d, "p95Nanos": %d}, "raw": {"medianNanos": %d, "p95Nanos": %d}},
                  "subsystems": {
                    "entities": {"clean": {"p95Nanos": %d}},
                    "chunk": {"clean": {"p95Nanos": %d}}
                  },
                  "network": {"bytesIn": %d, "bytesOut": %d},
                  "resourceSummary": {"maxCoreUtilization": {"median": 0.75, "p95": 0.90}}
                }
                """.formatted(medianNanos, p95Nanos, medianNanos, p95Nanos, p95Nanos / 2, p95Nanos / 4, bytesIn, bytesIn * 2));
    }
}
