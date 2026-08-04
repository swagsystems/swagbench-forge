package com.virgoagario.swagbench.resource;

public record ResourceMetricSummary(
        int count,
        double min,
        double median,
        double p95,
        double max,
        double slopePerSecond) {
    public static ResourceMetricSummary empty() {
        return new ResourceMetricSummary(0, -1.0, -1.0, -1.0, -1.0, -1.0);
    }
}
