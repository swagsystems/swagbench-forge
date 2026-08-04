package com.virgoagario.swagbench.core;

import java.util.EnumMap;
import java.util.Map;

public record TickSample(int tickIndex, long startNanos, long endNanos, Map<SubsystemBucket, Long> subsystemNanos) {
    public TickSample {
        if (endNanos < startNanos) {
            throw new IllegalArgumentException("endNanos must be >= startNanos");
        }
        EnumMap<SubsystemBucket, Long> copy = new EnumMap<>(SubsystemBucket.class);
        if (subsystemNanos != null) {
            subsystemNanos.forEach((bucket, nanos) -> {
                if (bucket == null) {
                    throw new IllegalArgumentException("bucket cannot be null");
                }
                if (nanos < 0) {
                    throw new IllegalArgumentException("bucket nanos must be >= 0");
                }
                copy.put(bucket, nanos);
            });
        }
        subsystemNanos = Map.copyOf(copy);
    }

    public static TickSample of(int tickIndex, long startNanos, long endNanos, Map<SubsystemBucket, Long> subsystemNanos) {
        return new TickSample(tickIndex, startNanos, endNanos, subsystemNanos);
    }

    public long durationNanos() {
        return endNanos - startNanos;
    }

    public long bucketNanos(SubsystemBucket bucket) {
        if (bucket == SubsystemBucket.OTHER) {
            return otherNanos();
        }
        return subsystemNanos.getOrDefault(bucket, 0L);
    }

    public long otherNanos() {
        return durationNanos() - namedSubsystemNanos();
    }

    public long namedSubsystemNanos() {
        long named = 0L;
        for (Map.Entry<SubsystemBucket, Long> entry : subsystemNanos.entrySet()) {
            if (entry.getKey() != SubsystemBucket.OTHER) {
                named += entry.getValue();
            }
        }
        return named;
    }
}
