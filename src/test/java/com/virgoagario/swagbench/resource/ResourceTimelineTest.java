package com.virgoagario.swagbench.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceTimelineTest {
    @Test
    void summarizesTrendMetricsAndIgnoresUnknownReadings() {
        ResourceTimeline timeline = ResourceTimeline.of(List.of(
                sample(0L, 100L, 0.25, 0L),
                sample(1_000_000_000L, 200L, -1.0, 1_000L),
                sample(2_000_000_000L, 300L, 0.75, 2_000L)));

        ResourceMetricSummary heap = timeline.summary().metric("heapUsedBytes");
        assertEquals(100.0, heap.min(), 0.0001);
        assertEquals(200.0, heap.median(), 0.0001);
        assertEquals(300.0, heap.p95(), 0.0001);
        assertEquals(300.0, heap.max(), 0.0001);
        assertEquals(100.0, heap.slopePerSecond(), 0.0001);

        ResourceMetricSummary cpu = timeline.summary().metric("processCpuLoad");
        assertEquals(0.25, cpu.min(), 0.0001);
        assertEquals(0.50, cpu.median(), 0.0001);
        assertEquals(0.75, cpu.max(), 0.0001);

        ResourceMetricSummary alloc = timeline.summary().metric("allocationRateBytesPerSecond");
        assertEquals(1_000.0, alloc.slopePerSecond(), 0.0001);
    }

    @Test
    void downsamplingPreservesFirstAndLastSampleUnderCap() {
        List<ResourceSample> samples = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            samples.add(sample(i, i, 0.1, i));
        }

        ResourceTimeline downsampled = ResourceTimeline.of(samples).downsampled(600);

        assertTrue(downsampled.samples().size() <= 600);
        assertEquals(samples.get(0), downsampled.samples().get(0));
        assertEquals(samples.get(samples.size() - 1), downsampled.samples().get(downsampled.samples().size() - 1));
    }

    private static ResourceSample sample(long nanoTime, long heapUsedBytes, double processCpuLoad, long allocationRate) {
        return new ResourceSample(
                nanoTime,
                processCpuLoad,
                0.0,
                List.of(0.1, 0.2),
                0L,
                0L,
                0L,
                heapUsedBytes,
                10L,
                heapUsedBytes - 5L,
                0L,
                0L,
                allocationRate,
                allocationRate);
    }
}
