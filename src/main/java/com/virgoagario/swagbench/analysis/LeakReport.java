package com.virgoagario.swagbench.analysis;

public record LeakReport(
        boolean leakSuspected,
        double postGcSlopeBytesPerMinute,
        double rSquared,
        int postGcSampleCount,
        boolean classHistogramEnabled,
        long entityCountGrowth,
        long blockEntityCountGrowth,
        long loadedChunkCountGrowth) {
}
