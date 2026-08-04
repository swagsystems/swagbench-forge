package com.virgoagario.swagbench.core;

public record BenchConfig(String scenario, long seed, int warmupTicks, int measureTicks) {
    public BenchConfig {
        if (scenario == null || scenario.isBlank()) {
            throw new IllegalArgumentException("scenario is required");
        }
        if (warmupTicks < 0) {
            throw new IllegalArgumentException("warmupTicks must be >= 0");
        }
        if (measureTicks <= 0) {
            throw new IllegalArgumentException("measureTicks must be > 0");
        }
    }

    public static BenchConfig tiny(String scenario, long seed, int warmupTicks, int measureTicks) {
        return new BenchConfig(scenario, seed, warmupTicks, measureTicks);
    }
}
