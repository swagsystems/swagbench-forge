package com.virgoagario.swagbench.core;

public record StatsBlock(
        int count,
        long minNanos,
        long medianNanos,
        long p50Nanos,
        long p90Nanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos,
        double meanNanos,
        double stddevNanos,
        double madNanos) {
    public static StatsBlock empty() {
        return new StatsBlock(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
    }
}
