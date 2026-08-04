package com.virgoagario.swagbench.core;

import java.util.Comparator;
import java.util.List;

public record RepeatabilitySummary(
        int runs,
        double medianSpreadRatio,
        double crossRunMadRatio,
        double maxCleanMadRatio,
        double thresholdRatio) {
    public RepeatabilitySummary {
        if (runs < 0) {
            throw new IllegalArgumentException("runs must be >= 0");
        }
        if (medianSpreadRatio < 0.0) {
            throw new IllegalArgumentException("medianSpreadRatio must be >= 0");
        }
        if (maxCleanMadRatio < 0.0) {
            throw new IllegalArgumentException("maxCleanMadRatio must be >= 0");
        }
        if (crossRunMadRatio < 0.0) {
            throw new IllegalArgumentException("crossRunMadRatio must be >= 0");
        }
        if (thresholdRatio < 0.0) {
            throw new IllegalArgumentException("thresholdRatio must be >= 0");
        }
    }

    public static RepeatabilitySummary fromStats(List<StatsBlock> stats, double thresholdRatio) {
        List<StatsBlock> safeStats = List.copyOf(stats == null ? List.of() : stats);
        if (safeStats.isEmpty()) {
            return new RepeatabilitySummary(0, 0.0, 0.0, 0.0, thresholdRatio);
        }

        List<Long> medians = safeStats.stream()
                .map(StatsBlock::medianNanos)
                .sorted()
                .toList();
        long min = medians.get(0);
        long max = medians.get(medians.size() - 1);
        double center = median(medians);
        double spreadRatio = center <= 0.0 ? 0.0 : (max - min) / center;
        List<Long> medianDeviations = medians.stream()
                .map(value -> Math.round(Math.abs(value - center)))
                .sorted()
                .toList();
        double crossRunMadRatio = center <= 0.0 ? 0.0 : median(medianDeviations) / center;
        double maxMadRatio = safeStats.stream()
                .filter(stat -> stat.medianNanos() > 0L)
                .map(stat -> stat.madNanos() / stat.medianNanos())
                .max(Comparator.naturalOrder())
                .orElse(0.0);

        return new RepeatabilitySummary(safeStats.size(), spreadRatio, crossRunMadRatio, maxMadRatio, thresholdRatio);
    }

    public boolean passes() {
        return crossRunMadRatio <= thresholdRatio && maxCleanMadRatio <= thresholdRatio;
    }

    private static double median(List<Long> sortedValues) {
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }
        return (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
    }
}
