package com.virgoagario.swagbench.resource;

import java.util.LinkedHashMap;
import java.util.Map;

public record ResourceSummary(Map<String, ResourceMetricSummary> metrics) {
    public ResourceSummary {
        metrics = Map.copyOf(metrics == null ? Map.of() : metrics);
    }

    public ResourceMetricSummary metric(String name) {
        return metrics.getOrDefault(name, ResourceMetricSummary.empty());
    }

    static ResourceSummary from(java.util.List<ResourceSample> samples) {
        Map<String, MetricValues> values = new LinkedHashMap<>();
        add(values, "processCpuLoad", samples, ResourceSample::processCpuLoad);
        add(values, "systemLoadAverage", samples, ResourceSample::systemLoadAverage);
        add(values, "maxCoreUtilization", samples, ResourceSample::maxCoreUtilization);
        add(values, "processCpuTimeNanos", samples, sample -> sample.processCpuTimeNanos());
        add(values, "serverThreadCpuTimeNanos", samples, sample -> sample.serverThreadCpuTimeNanos());
        add(values, "workerThreadCpuTimeNanos", samples, sample -> sample.workerThreadCpuTimeNanos());
        add(values, "heapUsedBytes", samples, sample -> sample.heapUsedBytes());
        add(values, "nonHeapUsedBytes", samples, sample -> sample.nonHeapUsedBytes());
        add(values, "postGcLiveSetBytes", samples, sample -> sample.postGcLiveSetBytes());
        add(values, "diskReadBytes", samples, sample -> sample.diskReadBytes());
        add(values, "diskWriteBytes", samples, sample -> sample.diskWriteBytes());
        add(values, "allocationRateBytesPerSecond", samples, sample -> sample.allocationRateBytesPerSecond());
        add(values, "allocatedBytesTotal", samples, sample -> sample.allocatedBytesTotal());

        Map<String, ResourceMetricSummary> summaries = new LinkedHashMap<>();
        for (Map.Entry<String, MetricValues> entry : values.entrySet()) {
            summaries.put(entry.getKey(), entry.getValue().summarize());
        }
        return new ResourceSummary(summaries);
    }

    private static void add(
            Map<String, MetricValues> metrics,
            String name,
            java.util.List<ResourceSample> samples,
            MetricExtractor extractor) {
        MetricValues values = new MetricValues();
        for (ResourceSample sample : samples) {
            double value = extractor.value(sample);
            if (Double.isFinite(value) && value >= 0.0) {
                values.add(sample.nanoTime(), value);
            }
        }
        metrics.put(name, values);
    }

    private interface MetricExtractor {
        double value(ResourceSample sample);
    }

    private static final class MetricValues {
        private final java.util.List<Long> times = new java.util.ArrayList<>();
        private final java.util.List<Double> values = new java.util.ArrayList<>();

        void add(long nanoTime, double value) {
            times.add(nanoTime);
            values.add(value);
        }

        ResourceMetricSummary summarize() {
            if (values.isEmpty()) {
                return ResourceMetricSummary.empty();
            }
            double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            return new ResourceMetricSummary(
                    sorted.length,
                    sorted[0],
                    median(sorted),
                    percentile(sorted, 0.95),
                    sorted[sorted.length - 1],
                    slopePerSecond());
        }

        private double slopePerSecond() {
            if (values.size() < 2) {
                return 0.0;
            }
            double meanTime = times.stream().mapToDouble(time -> time / 1_000_000_000.0).average().orElse(0.0);
            double meanValue = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double numerator = 0.0;
            double denominator = 0.0;
            for (int i = 0; i < values.size(); i++) {
                double x = times.get(i) / 1_000_000_000.0 - meanTime;
                double y = values.get(i) - meanValue;
                numerator += x * y;
                denominator += x * x;
            }
            return denominator == 0.0 ? 0.0 : numerator / denominator;
        }

        private static double percentile(double[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            index = Math.max(0, Math.min(index, sorted.length - 1));
            return sorted[index];
        }

        private static double median(double[] sorted) {
            int middle = sorted.length / 2;
            if (sorted.length % 2 == 1) {
                return sorted[middle];
            }
            return (sorted[middle - 1] + sorted[middle]) / 2.0;
        }
    }
}
