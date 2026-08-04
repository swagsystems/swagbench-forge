package com.virgoagario.swagbench.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StatsEngine {
    public static final String GC_DEGRADED_REASON = "gc_contamination_threshold_exceeded";
    public static final String ENVIRONMENT_DEGRADED_REASON = "environment_pressure_detected";
    public static final String BUCKET_RECONSTRUCTION_DEGRADED_REASON = "subsystem_bucket_reconstruction_failed";
    public static final String MAD_DEGRADED_REASON = "tick_mad_threshold_exceeded";
    private static final int TICK_WINDOW_SIZE = 100;
    private static final double CLEAN_MAD_TO_MEDIAN_THRESHOLD = 0.01;

    private final double gcContaminationThreshold;
    private final long bucketReconstructionToleranceNanos;

    public StatsEngine(double gcContaminationThreshold, long bucketReconstructionToleranceNanos) {
        if (gcContaminationThreshold < 0.0 || gcContaminationThreshold > 1.0) {
            throw new IllegalArgumentException("gcContaminationThreshold must be in [0, 1]");
        }
        if (bucketReconstructionToleranceNanos < 0L) {
            throw new IllegalArgumentException("bucketReconstructionToleranceNanos must be >= 0");
        }
        this.gcContaminationThreshold = gcContaminationThreshold;
        this.bucketReconstructionToleranceNanos = bucketReconstructionToleranceNanos;
    }

    public Report compute(
            BenchConfig config,
            List<TickSample> samples,
            List<GcEvent> gcEvents,
            EnvironmentInfo env,
            AllocationSummary alloc) {
        return compute(config, samples, gcEvents, env, RngInfo.unpinned(config.seed()), alloc);
    }

    public Report compute(
            BenchConfig config,
            List<TickSample> samples,
            List<GcEvent> gcEvents,
            EnvironmentInfo env,
            RngInfo rng,
            AllocationSummary alloc) {
        List<TickSample> safeSamples = List.copyOf(samples == null ? List.of() : samples);
        List<GcEvent> safeGcEvents = List.copyOf(gcEvents == null ? List.of() : gcEvents);
        List<TickSample> cleanSamples = new ArrayList<>();
        int contaminatedTicks = 0;

        for (TickSample sample : safeSamples) {
            if (isContaminated(sample, safeGcEvents)) {
                contaminatedTicks++;
            } else {
                cleanSamples.add(sample);
            }
        }

        StatsSet tick = new StatsSet(
                statsNanos(cleanSamples.stream().mapToLong(TickSample::durationNanos).toArray()),
                statsNanos(safeSamples.stream().mapToLong(TickSample::durationNanos).toArray()));
        StatsSet tickWindows = windowStats(safeSamples, safeGcEvents);
        Map<String, StatsSet> subsystems = subsystemStats(safeSamples, cleanSamples);
        double contaminationRatio = safeSamples.isEmpty() ? 0.0 : (double) contaminatedTicks / safeSamples.size();
        boolean gcDegraded = contaminationRatio > gcContaminationThreshold;
        boolean environmentDegraded = environmentPressureDetected(env);
        boolean bucketReconstructionDegraded = bucketReconstructionFailed(safeSamples);
        boolean madDegraded = cleanMadRatioExceeded(tickWindows.clean());
        boolean degraded = gcDegraded || environmentDegraded || bucketReconstructionDegraded || madDegraded;
        String degradedReason = "";
        if (gcDegraded) {
            degradedReason = GC_DEGRADED_REASON;
        } else if (environmentDegraded) {
            degradedReason = ENVIRONMENT_DEGRADED_REASON;
        } else if (bucketReconstructionDegraded) {
            degradedReason = BUCKET_RECONSTRUCTION_DEGRADED_REASON;
        } else if (madDegraded) {
            degradedReason = MAD_DEGRADED_REASON;
        }
        GcSummary gc = new GcSummary(
                safeGcEvents.size(),
                safeGcEvents.stream().mapToDouble(GcEvent::durationMillis).sum(),
                contaminatedTicks);

        return new Report(
                1,
                UUID.randomUUID().toString(),
                Instant.now(),
                env,
                config,
                rng,
                tick,
                tickWindows,
                subsystems,
                gc,
                alloc,
                degraded,
                degradedReason);
    }

    public long bucketReconstructionToleranceNanos() {
        return bucketReconstructionToleranceNanos;
    }

    private static boolean isContaminated(TickSample sample, List<GcEvent> gcEvents) {
        return gcEvents.stream().anyMatch(event -> event.overlaps(sample));
    }

    private static boolean cleanMadRatioExceeded(StatsBlock cleanStats) {
        return cleanStats.count() > 1
                && cleanStats.medianNanos() > 0L
                && cleanStats.madNanos() / cleanStats.medianNanos() > CLEAN_MAD_TO_MEDIAN_THRESHOLD;
    }

    private static boolean environmentPressureDetected(EnvironmentInfo env) {
        return env != null && env.cgroupSwapCurrent() > 0L;
    }

    private boolean bucketReconstructionFailed(List<TickSample> samples) {
        return samples.stream()
                .anyMatch(sample -> sample.namedSubsystemNanos() - sample.durationNanos()
                        > bucketReconstructionToleranceNanos);
    }

    private static Map<String, StatsSet> subsystemStats(List<TickSample> rawSamples, List<TickSample> cleanSamples) {
        Map<String, StatsSet> result = new LinkedHashMap<>();
        for (SubsystemBucket bucket : SubsystemBucket.values()) {
            result.put(bucket.schemaName(), new StatsSet(bucketStats(cleanSamples, bucket), bucketStats(rawSamples, bucket)));
        }
        return result;
    }

    private static StatsSet windowStats(List<TickSample> rawSamples, List<GcEvent> gcEvents) {
        List<Long> rawWindows = new ArrayList<>();
        List<Long> cleanWindows = new ArrayList<>();
        for (int start = 0; start < rawSamples.size(); start += TICK_WINDOW_SIZE) {
            int end = Math.min(start + TICK_WINDOW_SIZE, rawSamples.size());
            if (end - start < TICK_WINDOW_SIZE) {
                break;
            }
            long duration = 0L;
            boolean contaminated = false;
            for (int i = start; i < end; i++) {
                TickSample sample = rawSamples.get(i);
                duration += sample.durationNanos();
                contaminated |= isContaminated(sample, gcEvents);
            }
            long windowMean = Math.round(duration / (double) TICK_WINDOW_SIZE);
            rawWindows.add(windowMean);
            if (!contaminated) {
                cleanWindows.add(windowMean);
            }
        }
        return new StatsSet(
                statsNanos(cleanWindows.stream().mapToLong(Long::longValue).toArray()),
                statsNanos(rawWindows.stream().mapToLong(Long::longValue).toArray()));
    }

    private static StatsBlock bucketStats(List<TickSample> samples, SubsystemBucket bucket) {
        return statsNanos(samples.stream().mapToLong(sample -> sample.bucketNanos(bucket)).toArray());
    }

    public static StatsBlock statsNanos(long[] values) {
        if (values == null || values.length == 0) {
            return StatsBlock.empty();
        }

        long[] sorted = values.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElse(0.0);
        double variance = 0.0;
        for (long value : sorted) {
            double delta = value - mean;
            variance += delta * delta;
        }
        variance /= sorted.length;

        long median = median(sorted);
        long[] deviations = new long[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            deviations[i] = Math.abs(sorted[i] - median);
        }
        Arrays.sort(deviations);

        return new StatsBlock(
                sorted.length,
                sorted[0],
                median,
                percentile(sorted, 0.50),
                percentile(sorted, 0.90),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                sorted[sorted.length - 1],
                mean,
                Math.sqrt(variance),
                median(deviations));
    }

    private static long median(long[] sorted) {
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[middle];
        }
        return Math.round((sorted[middle - 1] + sorted[middle]) / 2.0);
    }

    private static long percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index];
    }
}
