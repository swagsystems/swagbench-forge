package com.virgoagario.swagbench.core;

public record GcEvent(String collectorName, long startNanos, long endNanos) {
    public GcEvent {
        if (collectorName == null || collectorName.isBlank()) {
            collectorName = "unknown";
        }
        if (endNanos < startNanos) {
            throw new IllegalArgumentException("endNanos must be >= startNanos");
        }
    }

    public boolean overlaps(TickSample sample) {
        return startNanos < sample.endNanos() && endNanos > sample.startNanos();
    }

    public long durationNanos() {
        return endNanos - startNanos;
    }

    public double durationMillis() {
        return durationNanos() / 1_000_000.0;
    }
}
