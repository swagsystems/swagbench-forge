package com.virgoagario.swagbench.analysis;

import java.util.List;

public record BottleneckReport(String overallBottleneck, List<BottleneckWindow> windows) {
    public BottleneckReport {
        overallBottleneck = overallBottleneck == null || overallBottleneck.isBlank() ? "unknown" : overallBottleneck;
        windows = List.copyOf(windows == null ? List.of() : windows);
    }

    public static BottleneckReport empty() {
        return new BottleneckReport("none", List.of());
    }
}
