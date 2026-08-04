package com.virgoagario.swagbench.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.core.TickSample;
import com.virgoagario.swagbench.network.NetworkSummary;
import com.virgoagario.swagbench.resource.ResourceSample;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BottleneckAttributorTest {
    @Test
    void ranksPeggedCoreAsLikelyBottleneckForDegradedWindow() {
        BottleneckReport report = new BottleneckAttributor().analyze(
                ticks(100, 10_000_000L, 90_000_000L, SubsystemBucket.ENTITIES, 20_000_000L),
                ResourceTimeline.of(List.of(
                        sample(0L, 0.50, 0L, 0L),
                        sample(250_000_000L, 0.99, 0L, 0L),
                        sample(500_000_000L, 1.00, 0L, 0L))),
                new NetworkSummary(1, 128L, 256L, List.of()),
                0.0);

        assertEquals("cpu", report.overallBottleneck());
        assertFalse(report.windows().isEmpty());
        assertEquals("cpu", report.windows().get(0).topRank());
    }

    @Test
    void fallsBackToDominantSubsystemWhenResourcesAreNotSaturated() {
        BottleneckReport report = new BottleneckAttributor().analyze(
                ticks(100, 10_000_000L, 80_000_000L, SubsystemBucket.CHUNK, 65_000_000L),
                ResourceTimeline.of(List.of(sample(0L, 0.35, 0L, 0L), sample(250_000_000L, 0.40, 0L, 0L))),
                NetworkSummary.empty(),
                0.0);

        assertEquals("subsystem:chunk", report.overallBottleneck());
        assertEquals("chunk", report.windows().get(0).dominantSubsystem());
    }

    @Test
    void ignoresCpuBurstOutsideTheDegradedWindow() {
        BottleneckReport report = new BottleneckAttributor().analyze(
                ticks(200, 10_000_000L, 80_000_000L, SubsystemBucket.CHUNK, 65_000_000L),
                ResourceTimeline.of(List.of(
                        sample(0L, 1.00, 0L, 0L),
                        sample(1_000_000_000L, 0.35, 0L, 0L),
                        sample(3_000_000_000L, 0.40, 0L, 0L))),
                NetworkSummary.empty(),
                0.0);

        assertEquals("subsystem:chunk", report.overallBottleneck());
    }

    private static List<TickSample> ticks(
            int count,
            long baselineNanos,
            long degradedNanos,
            SubsystemBucket bucket,
            long bucketNanos) {
        List<TickSample> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long duration = i < 50 ? baselineNanos : degradedNanos;
            long named = i < 50 ? Math.min(bucketNanos, baselineNanos / 2) : bucketNanos;
            samples.add(TickSample.of(i, i * 50_000_000L, i * 50_000_000L + duration, Map.of(bucket, named)));
        }
        return samples;
    }

    private static ResourceSample sample(long nanoTime, double coreUtilization, long diskWriteBytes, long allocationRate) {
        return new ResourceSample(
                nanoTime,
                coreUtilization,
                1.0,
                List.of(coreUtilization),
                nanoTime,
                nanoTime / 2,
                nanoTime / 4,
                512L,
                64L,
                -1L,
                0L,
                diskWriteBytes,
                allocationRate,
                allocationRate);
    }
}
