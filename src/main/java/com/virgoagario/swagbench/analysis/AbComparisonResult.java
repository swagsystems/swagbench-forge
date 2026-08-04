package com.virgoagario.swagbench.analysis;

import java.util.List;

public record AbComparisonResult(List<MetricComparison> metrics) {
    public AbComparisonResult {
        metrics = List.copyOf(metrics == null ? List.of() : metrics);
    }

    public MetricComparison metric(String name) {
        return metrics.stream()
                .filter(metric -> metric.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown metric: " + name));
    }

    public String toText() {
        StringBuilder text = new StringBuilder();
        text.append("metric,baselineMedian,candidateMedian,delta,deltaPercent,ciLow,ciHigh,verdict\n");
        for (MetricComparison metric : metrics) {
            text.append(metric.name()).append(',')
                    .append(metric.baselineMedian()).append(',')
                    .append(metric.candidateMedian()).append(',')
                    .append(metric.delta()).append(',')
                    .append(metric.deltaPercent()).append(',')
                    .append(metric.ciLow()).append(',')
                    .append(metric.ciHigh()).append(',')
                    .append(metric.verdict()).append('\n');
        }
        return text.toString();
    }

    public record MetricComparison(
            String name,
            double baselineMedian,
            double candidateMedian,
            double delta,
            double deltaPercent,
            double ciLow,
            double ciHigh,
            String verdict) {
    }
}
