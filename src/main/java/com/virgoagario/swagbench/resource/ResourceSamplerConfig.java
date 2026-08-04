package com.virgoagario.swagbench.resource;

public record ResourceSamplerConfig(boolean enabled, long intervalMillis, boolean fullTimeline) {
    public static final long DEFAULT_INTERVAL_MILLIS = 250L;

    public ResourceSamplerConfig {
        if (intervalMillis <= 0L) {
            intervalMillis = DEFAULT_INTERVAL_MILLIS;
        }
    }

    public static ResourceSamplerConfig disabled() {
        return new ResourceSamplerConfig(false, DEFAULT_INTERVAL_MILLIS, false);
    }
}
