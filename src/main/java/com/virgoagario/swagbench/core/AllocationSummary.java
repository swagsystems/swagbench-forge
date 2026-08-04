package com.virgoagario.swagbench.core;

public record AllocationSummary(boolean enabled, long bytesTotal, long bytesPerTickMedian) {
    public static AllocationSummary disabled() {
        return new AllocationSummary(false, 0L, 0L);
    }
}
