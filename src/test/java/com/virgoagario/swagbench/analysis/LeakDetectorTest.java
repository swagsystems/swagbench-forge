package com.virgoagario.swagbench.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virgoagario.swagbench.resource.ResourceSample;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeakDetectorTest {
    @Test
    void flagsHighConfidencePostGcLiveSetGrowth() {
        ResourceTimeline timeline = ResourceTimeline.of(List.of(
                sample(0L, 100_000_000L),
                sample(60_000_000_000L, 130_000_000L),
                sample(120_000_000_000L, 160_000_000L),
                sample(180_000_000_000L, 190_000_000L)));

        LeakReport report = new LeakDetector(10_000_000.0, 0.90).analyze(timeline);

        assertTrue(report.leakSuspected());
        assertEquals(30_000_000.0, report.postGcSlopeBytesPerMinute(), 1.0);
        assertTrue(report.rSquared() > 0.99);
        assertEquals(4, report.postGcSampleCount());
    }

    @Test
    void flatPostGcLiveSetDoesNotFlagLeak() {
        ResourceTimeline timeline = ResourceTimeline.of(List.of(
                sample(0L, 100_000_000L),
                sample(60_000_000_000L, 100_500_000L),
                sample(120_000_000_000L, 99_500_000L),
                sample(180_000_000_000L, 100_000_000L)));

        LeakReport report = new LeakDetector(10_000_000.0, 0.90).analyze(timeline);

        assertFalse(report.leakSuspected());
        assertTrue(Math.abs(report.postGcSlopeBytesPerMinute()) < 1_000_000.0);
    }

    @Test
    void repeatedLatestGcValueDoesNotInflateSampleCount() {
        ResourceTimeline timeline = ResourceTimeline.of(List.of(
                sample(0L, -1L),
                sample(60_000_000_000L, 100_000_000L),
                sample(61_000_000_000L, 100_000_000L),
                sample(62_000_000_000L, 100_000_000L),
                sample(120_000_000_000L, 140_000_000L),
                sample(121_000_000_000L, 140_000_000L)));

        LeakReport report = new LeakDetector(10_000_000.0, 0.90).analyze(timeline);

        assertEquals(2, report.postGcSampleCount());
        assertEquals(40_000_000.0, report.postGcSlopeBytesPerMinute(), 1.0);
    }

    @Test
    void reportsEntityBlockEntityAndChunkCountGrowth() {
        LeakReport report = new LeakDetector().analyze(ResourceTimeline.of(List.of()), 10L, 14L, -1L, -1L, 20L, 25L);

        assertEquals(4L, report.entityCountGrowth());
        assertEquals(-1L, report.blockEntityCountGrowth());
        assertEquals(5L, report.loadedChunkCountGrowth());
    }

    private static ResourceSample sample(long nanoTime, long postGcLiveSetBytes) {
        return new ResourceSample(
                nanoTime,
                0.25,
                1.0,
                List.of(0.25),
                nanoTime,
                nanoTime / 2,
                nanoTime / 4,
                postGcLiveSetBytes,
                64L,
                postGcLiveSetBytes,
                0L,
                0L,
                0L,
                0L);
    }
}
