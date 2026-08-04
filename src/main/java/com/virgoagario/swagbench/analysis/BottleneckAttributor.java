package com.virgoagario.swagbench.analysis;

import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.core.TickSample;
import com.virgoagario.swagbench.network.NetworkSummary;
import com.virgoagario.swagbench.resource.ResourceSample;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BottleneckAttributor {
    private static final int WINDOW_TICKS = 100;
    private static final long ABSOLUTE_DEGRADED_NANOS = 50_000_000L;
    private static final double RELATIVE_DEGRADED_MULTIPLIER = 1.5;
    private static final double CPU_SATURATION = 0.95;
    private static final long DISK_BYTES_PER_WINDOW = 16L * 1024L * 1024L;
    private static final long NETWORK_BYTES_PER_WINDOW = 16L * 1024L * 1024L;

    public BottleneckReport analyze(
            List<TickSample> samples,
            ResourceTimeline resources,
            NetworkSummary network,
            double gcPausePercent) {
        List<TickSample> safeSamples = List.copyOf(samples == null ? List.of() : samples);
        if (safeSamples.isEmpty()) {
            return BottleneckReport.empty();
        }
        long baseline = baselineNanos(safeSamples);
        List<BottleneckWindow> windows = new ArrayList<>();
        for (int start = 0; start < safeSamples.size(); start += WINDOW_TICKS) {
            int end = Math.min(start + WINDOW_TICKS, safeSamples.size());
            if (end <= start) {
                continue;
            }
            long mean = Math.round(safeSamples.subList(start, end).stream()
                    .mapToLong(TickSample::durationNanos)
                    .average()
                    .orElse(0.0));
            if (mean < ABSOLUTE_DEGRADED_NANOS && mean < Math.round(baseline * RELATIVE_DEGRADED_MULTIPLIER)) {
                continue;
            }
            List<TickSample> windowSamples = safeSamples.subList(start, end);
            windows.add(attributeWindow(
                    start,
                    end - 1,
                    mean,
                    windowSamples,
                    windowResources(resources, windowSamples),
                    network,
                    gcPausePercent));
        }
        if (windows.isEmpty()) {
            return new BottleneckReport("none", List.of());
        }
        return new BottleneckReport(overall(windows), windows);
    }

    private static BottleneckWindow attributeWindow(
            int startTick,
            int endTick,
            long mean,
            List<TickSample> samples,
            ResourceTimeline resources,
            NetworkSummary network,
            double gcPausePercent) {
        double maxCore = maxCore(resources);
        long diskBytes = diskBytes(resources);
        long networkBytes = network == null ? 0L : Math.max(0L, network.bytesIn()) + Math.max(0L, network.bytesOut());
        SubsystemEvidence subsystem = dominantSubsystem(samples);
        Map<String, Double> evidence = new LinkedHashMap<>();
        evidence.put("maxCoreUtilization", maxCore);
        evidence.put("gcPausePercent", gcPausePercent);
        evidence.put("diskBytes", (double) diskBytes);
        evidence.put("networkBytes", (double) networkBytes);
        evidence.put("dominantSubsystemShare", subsystem.share());

        String topRank;
        if (maxCore >= CPU_SATURATION) {
            topRank = "cpu";
        } else if (gcPausePercent >= 10.0) {
            topRank = "gc";
        } else if (diskBytes >= DISK_BYTES_PER_WINDOW) {
            topRank = "disk";
        } else if (networkBytes >= NETWORK_BYTES_PER_WINDOW) {
            topRank = "network";
        } else {
            topRank = "subsystem:" + subsystem.name();
        }
        return new BottleneckWindow(startTick, endTick, mean, topRank, subsystem.name(), evidence);
    }

    private static long baselineNanos(List<TickSample> samples) {
        long[] durations = samples.stream().mapToLong(TickSample::durationNanos).sorted().toArray();
        if (durations.length == 0) {
            return 0L;
        }
        int lowerHalf = Math.max(1, durations.length / 2);
        long[] baseline = Arrays.copyOf(durations, lowerHalf);
        return median(baseline);
    }

    private static long median(long[] sorted) {
        if (sorted.length == 0) {
            return 0L;
        }
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[middle];
        }
        return Math.round((sorted[middle - 1] + sorted[middle]) / 2.0);
    }

    private static double maxCore(ResourceTimeline resources) {
        if (resources == null) {
            return -1.0;
        }
        return resources.samples().stream()
                .flatMap(sample -> sample.perCoreUtilization().stream())
                .filter(value -> value >= 0.0)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(-1.0);
    }

    private static long diskBytes(ResourceTimeline resources) {
        if (resources == null) {
            return 0L;
        }
        return resources.samples().stream()
                .mapToLong(sample -> Math.max(0L, sample.diskReadBytes()) + Math.max(0L, sample.diskWriteBytes()))
                .sum();
    }

    private static ResourceTimeline windowResources(ResourceTimeline resources, List<TickSample> samples) {
        if (resources == null || samples.isEmpty()) {
            return null;
        }
        long start = samples.get(0).startNanos();
        long end = samples.get(samples.size() - 1).endNanos();
        List<com.virgoagario.swagbench.resource.ResourceSample> selected = resources.samples().stream()
                .filter(sample -> sample.nanoTime() >= start && sample.nanoTime() <= end)
                .toList();
        return ResourceTimeline.of(selected);
    }

    private static SubsystemEvidence dominantSubsystem(List<TickSample> samples) {
        Map<SubsystemBucket, Long> totals = new java.util.EnumMap<>(SubsystemBucket.class);
        long totalDuration = 0L;
        for (TickSample sample : samples) {
            totalDuration += sample.durationNanos();
            for (SubsystemBucket bucket : SubsystemBucket.values()) {
                totals.merge(bucket, sample.bucketNanos(bucket), Long::sum);
            }
        }
        Map.Entry<SubsystemBucket, Long> top = totals.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .orElse(Map.entry(SubsystemBucket.OTHER, 0L));
        double share = totalDuration <= 0L ? 0.0 : top.getValue() / (double) totalDuration;
        return new SubsystemEvidence(top.getKey().schemaName(), share);
    }

    private static String overall(List<BottleneckWindow> windows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BottleneckWindow window : windows) {
            counts.merge(window.topRank(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    private record SubsystemEvidence(String name, double share) {
    }
}
