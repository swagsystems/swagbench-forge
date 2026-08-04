package com.virgoagario.swagbench.analysis;

import java.util.Map;

public record BottleneckWindow(
        int startTick,
        int endTick,
        long meanMsptNanos,
        String topRank,
        String dominantSubsystem,
        Map<String, Double> evidence) {
    public BottleneckWindow {
        topRank = topRank == null || topRank.isBlank() ? "unknown" : topRank;
        dominantSubsystem = dominantSubsystem == null || dominantSubsystem.isBlank() ? "unknown" : dominantSubsystem;
        evidence = Map.copyOf(evidence == null ? Map.of() : evidence);
    }
}
