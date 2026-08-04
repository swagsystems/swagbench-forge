package com.virgoagario.swagbench.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatsEngineTest {
    @Test
    void computesRobustDistributionStatsInNanoseconds() {
        StatsBlock stats = StatsEngine.statsNanos(new long[] {10, 20, 30, 40, 50});

        assertEquals(5, stats.count());
        assertEquals(10, stats.minNanos());
        assertEquals(30, stats.medianNanos());
        assertEquals(30, stats.p50Nanos());
        assertEquals(50, stats.p90Nanos());
        assertEquals(50, stats.p95Nanos());
        assertEquals(50, stats.p99Nanos());
        assertEquals(50, stats.maxNanos());
        assertEquals(30.0, stats.meanNanos(), 0.0001);
        assertEquals(14.1421, stats.stddevNanos(), 0.0001);
        assertEquals(10.0, stats.madNanos(), 0.0001);
    }

    @Test
    void reportsCleanAndRawTickStatsWhenGcOverlapsMeasurementTicks() {
        List<TickSample> samples = List.of(
                TickSample.of(0, 0, 100_000_000, Map.of()),
                TickSample.of(1, 100_000_000, 300_000_000, Map.of()),
                TickSample.of(2, 300_000_000, 400_000_000, Map.of()));
        List<GcEvent> gcEvents = List.of(new GcEvent("G1 Young Generation", 150_000_000, 175_000_000));

        Report report = new StatsEngine(0.50, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 2, 3),
                samples,
                gcEvents,
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        assertEquals(3, report.tick().raw().count());
        assertEquals(2, report.tick().clean().count());
        assertEquals(1, report.gc().contaminatedTicks());
        assertEquals(25.0, report.gc().totalPauseMs(), 0.0001);
        assertFalse(report.degraded());
    }

    @Test
    void flagsDegradedReportsWhenGcContaminationExceedsThreshold() {
        List<TickSample> samples = List.of(
                TickSample.of(0, 0, 100_000_000, Map.of()),
                TickSample.of(1, 100_000_000, 200_000_000, Map.of()));
        List<GcEvent> gcEvents = List.of(new GcEvent("G1 Old Generation", 25_000_000, 175_000_000));

        Report report = new StatsEngine(0.25, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, 2),
                samples,
                gcEvents,
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        assertTrue(report.degraded());
        assertEquals("gc_contamination_threshold_exceeded", report.degradedReason());
    }

    @Test
    void keepsShortRunsNonDegradedUntilAFullWindowExists() {
        List<TickSample> samples = List.of(
                TickSample.of(0, 0, 1_000, Map.of()),
                TickSample.of(1, 1_000, 2_000, Map.of()),
                TickSample.of(2, 2_000, 10_000, Map.of()),
                TickSample.of(3, 10_000, 18_000, Map.of()));

        Report report = new StatsEngine(0.50, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, 4),
                samples,
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        assertEquals(4, report.tick().clean().count());
        assertEquals(0, report.tickWindows().clean().count());
        assertFalse(report.degraded());
        assertEquals("", report.degradedReason());
    }

    @Test
    void preservesPerTickMadAsDiagnosticWhenWindowPolicyPasses() {
        Report report = reportForTickDurations(960, 1_000, 1_000, 1_040);

        assertEquals(1_000, report.tick().clean().medianNanos());
        assertEquals(20.0, report.tick().clean().madNanos(), 0.0001);
        assertEquals(0, report.tickWindows().clean().count());
        assertFalse(report.degraded());
        assertEquals("", report.degradedReason());
    }

    @Test
    void keepsSubPercentCleanTickMadNonDegradedWhenGcIsClean() {
        Report report = reportForTickDurations(990, 1_000, 1_000, 1_010);

        assertEquals(1_000, report.tick().clean().medianNanos());
        assertEquals(5.0, report.tick().clean().madNanos(), 0.0001);
        assertFalse(report.degraded());
        assertEquals("", report.degradedReason());
    }

    @Test
    void computesWindowedTickStatsFromFixedSizeWindowMeans() {
        List<TickSample> samples = new java.util.ArrayList<>();
        long start = 0L;
        for (int i = 0; i < 100; i++) {
            long duration = i % 2 == 0 ? 1_000L : 2_000L;
            samples.add(TickSample.of(i, start, start + duration, Map.of()));
            start += duration;
        }
        for (int i = 100; i < 200; i++) {
            long duration = i % 2 == 0 ? 1_000L : 2_000L;
            samples.add(TickSample.of(i, start, start + duration, Map.of()));
            start += duration;
        }

        Report report = new StatsEngine(0.50, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, samples.size()),
                samples,
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        assertEquals(200, report.tick().clean().count());
        assertEquals(2, report.tickWindows().clean().count());
        assertEquals(1_500, report.tickWindows().clean().medianNanos());
        assertEquals(0.0, report.tickWindows().clean().madNanos(), 0.0001);
        assertFalse(report.degraded());
    }

    @Test
    void flagsDegradedReportsWhenWindowedCleanTickMadIsTooHigh() {
        List<TickSample> samples = new java.util.ArrayList<>();
        long start = 0L;
        for (int i = 0; i < 300; i++) {
            long duration = 1_000L + ((i / 100) * 50L);
            samples.add(TickSample.of(i, start, start + duration, Map.of()));
            start += duration;
        }

        Report report = new StatsEngine(0.50, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, samples.size()),
                samples,
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        assertEquals(3, report.tickWindows().clean().count());
        assertEquals(1_050, report.tickWindows().clean().medianNanos());
        assertEquals(50.0, report.tickWindows().clean().madNanos(), 0.0001);
        assertTrue(report.degraded());
        assertEquals("tick_mad_threshold_exceeded", report.degradedReason());
    }

    @Test
    void flagsDegradedReportsWhenCgroupSwapIsAlreadyInUse() {
        List<TickSample> samples = new java.util.ArrayList<>();
        long start = 0L;
        for (int i = 0; i < 200; i++) {
            samples.add(TickSample.of(i, start, start + 1_000L, Map.of()));
            start += 1_000L;
        }

        Report report = new StatsEngine(0.50, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, samples.size()),
                samples,
                List.of(),
                environmentWithCgroupSwap(1024L),
                AllocationSummary.disabled());

        assertTrue(report.degraded());
        assertEquals("environment_pressure_detected", report.degradedReason());
    }

    @Test
    void keepsSyntheticEnvironmentFreeOfHostPressureForUnitTests() {
        EnvironmentInfo env = EnvironmentInfo.synthetic();

        assertEquals(0L, env.cgroupSwapCurrent());
        assertEquals(0.0, env.systemLoadAverage(), 0.0001);
    }

    @Test
    void subsystemOtherBucketReconstructsMeasuredTickNanos() {
        TickSample sample = TickSample.of(0, 0, 1_000, Map.of(
                SubsystemBucket.ENTITIES, 200L,
                SubsystemBucket.BLOCK_ENTITIES, 100L,
                SubsystemBucket.CHUNK, 50L,
                SubsystemBucket.SCHEDULED, 25L));

        Report report = new StatsEngine(0.25, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, 1),
                List.of(sample),
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        assertEquals(625, report.subsystems().get(SubsystemBucket.OTHER.schemaName()).raw().medianNanos());
        assertEquals(1_000, report.tick().raw().medianNanos());
    }

    @Test
    void flagsDegradedReportsWhenNamedBucketsOvercountTickTimeBeyondTolerance() {
        TickSample sample = TickSample.of(0, 0, 1_000, Map.of(
                SubsystemBucket.ENTITIES, 800L,
                SubsystemBucket.CHUNK, 400L));

        Report report = new StatsEngine(0.25, 100L).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, 1),
                List.of(sample),
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        assertEquals(-200, report.subsystems().get(SubsystemBucket.OTHER.schemaName()).raw().medianNanos());
        assertTrue(report.degraded());
        assertEquals("subsystem_bucket_reconstruction_failed", report.degradedReason());
    }

    private static Report reportForTickDurations(long... durationsNanos) {
        long start = 0L;
        List<TickSample> samples = new java.util.ArrayList<>();
        for (int i = 0; i < durationsNanos.length; i++) {
            long end = start + durationsNanos[i];
            samples.add(TickSample.of(i, start, end, Map.of()));
            start = end;
        }
        return new StatsEngine(0.50, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 42L, 1, durationsNanos.length),
                samples,
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());
    }

    private static EnvironmentInfo environmentWithCgroupSwap(long cgroupSwapCurrent) {
        return new EnvironmentInfo(
                "1.18.2",
                "40.3.12",
                "17",
                List.of("-Xmx2G"),
                "Linux",
                "amd64 4 cores",
                2_147_483_648L,
                0.0,
                12_884_901_888L,
                6_442_450_944L,
                2_147_483_648L,
                2_147_483_648L,
                7_000_000_000L,
                -1L,
                cgroupSwapCurrent,
                -1L,
                List.of("swagbench@1.0.0"));
    }
}
