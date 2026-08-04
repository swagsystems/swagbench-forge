package com.virgoagario.swagbench.resource;

import java.util.List;

public record ResourceSample(
        long nanoTime,
        double processCpuLoad,
        double systemLoadAverage,
        List<Double> perCoreUtilization,
        long processCpuTimeNanos,
        long serverThreadCpuTimeNanos,
        long workerThreadCpuTimeNanos,
        long heapUsedBytes,
        long nonHeapUsedBytes,
        long postGcLiveSetBytes,
        long diskReadBytes,
        long diskWriteBytes,
        long allocationRateBytesPerSecond,
        long allocatedBytesTotal) {
    public ResourceSample {
        perCoreUtilization = List.copyOf(perCoreUtilization == null ? List.of() : perCoreUtilization);
    }

    double maxCoreUtilization() {
        return perCoreUtilization.stream()
                .filter(value -> value >= 0.0)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(-1.0);
    }
}
