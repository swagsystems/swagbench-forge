package com.virgoagario.swagbench.core;

import com.virgoagario.swagbench.analysis.BottleneckWindow;
import com.virgoagario.swagbench.load.LoadSummary;
import com.virgoagario.swagbench.network.NetworkConnectionSummary;
import com.virgoagario.swagbench.network.NetworkSummary;
import com.virgoagario.swagbench.resource.ResourceMetricSummary;
import com.virgoagario.swagbench.resource.ResourceSample;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ReportWriter {
    public Path write(Report report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path path = outputDir.resolve("swagbench-" + report.runId() + ".json");
        Files.writeString(path, toJson(report), StandardCharsets.UTF_8);
        return path;
    }

    public String toJson(Report report) {
        StringBuilder json = new StringBuilder(4096);
        json.append('{');
        field(json, "schemaVersion", report.schemaVersion()).append(',');
        field(json, "mode", report.mode()).append(',');
        field(json, "runId", report.runId()).append(',');
        field(json, "timestampUtc", report.timestampUtc().toString()).append(',');
        json.append("\"env\":");
        env(json, report.env()).append(',');
        json.append("\"config\":");
        config(json, report.config()).append(',');
        json.append("\"rng\":");
        rng(json, report.rng()).append(',');
        json.append("\"tick\":");
        statsSet(json, report.tick()).append(',');
        json.append("\"tickWindows\":");
        statsSet(json, report.tickWindows()).append(',');
        json.append("\"subsystems\":{");
        boolean first = true;
        for (Map.Entry<String, StatsSet> entry : report.subsystems().entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            quoted(json, entry.getKey()).append(':');
            statsSet(json, entry.getValue());
        }
        json.append("},");
        json.append("\"gc\":");
        gc(json, report.gc()).append(',');
        json.append("\"alloc\":");
        alloc(json, report.alloc()).append(',');
        if (report.resources() != null) {
            json.append("\"resourceSummary\":");
            resourceSummary(json, report.resources()).append(',');
            json.append("\"resourceTimeline\":");
            resourceTimeline(json, report.resources()).append(',');
        }
        if (report.load() != null) {
            json.append("\"load\":");
            load(json, report.load()).append(',');
        }
        if (report.network() != null) {
            json.append("\"network\":");
            network(json, report.network()).append(',');
        }
        if (report.bottleneck() != null) {
            json.append("\"bottleneck\":");
            bottleneck(json, report.bottleneck()).append(',');
        }
        if (report.leak() != null) {
            json.append("\"leak\":");
            leak(json, report.leak()).append(',');
        }
        field(json, "degraded", report.degraded()).append(',');
        field(json, "degradedReason", report.degradedReason());
        json.append('}');
        return json.toString();
    }

    private static StringBuilder env(StringBuilder json, EnvironmentInfo env) {
        json.append('{');
        field(json, "mcVersion", env.mcVersion()).append(',');
        field(json, "forge", env.forge()).append(',');
        field(json, "java", env.java()).append(',');
        json.append("\"jvmArgs\":");
        stringArray(json, env.jvmArgs()).append(',');
        field(json, "os", env.os()).append(',');
        field(json, "cpu", env.cpu()).append(',');
        field(json, "heapMax", env.heapMax()).append(',');
        field(json, "systemLoadAverage", env.systemLoadAverage()).append(',');
        field(json, "totalPhysicalMemory", env.totalPhysicalMemory()).append(',');
        field(json, "freePhysicalMemory", env.freePhysicalMemory()).append(',');
        field(json, "totalSwap", env.totalSwap()).append(',');
        field(json, "freeSwap", env.freeSwap()).append(',');
        field(json, "cgroupMemoryCurrent", env.cgroupMemoryCurrent()).append(',');
        field(json, "cgroupMemoryMax", env.cgroupMemoryMax()).append(',');
        field(json, "cgroupSwapCurrent", env.cgroupSwapCurrent()).append(',');
        field(json, "cgroupSwapMax", env.cgroupSwapMax()).append(',');
        json.append("\"mods\":");
        stringArray(json, env.mods());
        json.append('}');
        return json;
    }

    private static StringBuilder load(StringBuilder json, LoadSummary load) {
        json.append('{');
        field(json, "mode", load.mode()).append(',');
        field(json, "targetClients", load.targetClients()).append(',');
        field(json, "connectedClients", load.connectedClients()).append(',');
        field(json, "failedClients", load.failedClients()).append(',');
        field(json, "rampSeconds", load.rampSeconds()).append(',');
        field(json, "holdSeconds", load.holdSeconds()).append(',');
        field(json, "moveHz", load.moveHz()).append(',');
        field(json, "seed", load.seed()).append(',');
        field(json, "reachedTarget", load.reachedTarget());
        json.append('}');
        return json;
    }

    private static StringBuilder network(StringBuilder json, NetworkSummary network) {
        json.append('{');
        field(json, "totalConnections", network.connections()).append(',');
        field(json, "bytesIn", network.bytesIn()).append(',');
        field(json, "bytesOut", network.bytesOut()).append(',');
        json.append("\"connections\":[");
        boolean first = true;
        for (NetworkConnectionSummary connection : network.connectionSummaries()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", connection.id()).append(',');
            field(json, "bytesIn", connection.bytesIn()).append(',');
            field(json, "bytesOut", connection.bytesOut());
            json.append('}');
        }
        json.append("]}");
        return json;
    }

    private static StringBuilder bottleneck(
            StringBuilder json,
            com.virgoagario.swagbench.analysis.BottleneckReport bottleneck) {
        json.append('{');
        field(json, "overall", bottleneck.overallBottleneck()).append(',');
        json.append("\"windows\":[");
        boolean first = true;
        for (BottleneckWindow window : bottleneck.windows()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "startTick", window.startTick()).append(',');
            field(json, "endTick", window.endTick()).append(',');
            field(json, "meanMsptNanos", window.meanMsptNanos()).append(',');
            field(json, "topRank", window.topRank()).append(',');
            field(json, "dominantSubsystem", window.dominantSubsystem()).append(',');
            json.append("\"evidence\":");
            doubleMap(json, window.evidence());
            json.append('}');
        }
        json.append("]}");
        return json;
    }

    private static StringBuilder leak(StringBuilder json, com.virgoagario.swagbench.analysis.LeakReport leak) {
        json.append('{');
        field(json, "leakSuspected", leak.leakSuspected()).append(',');
        field(json, "postGcSlopeBytesPerMinute", leak.postGcSlopeBytesPerMinute()).append(',');
        field(json, "rSquared", leak.rSquared()).append(',');
        field(json, "postGcSampleCount", leak.postGcSampleCount()).append(',');
        field(json, "classHistogramEnabled", leak.classHistogramEnabled()).append(',');
        field(json, "entityCountGrowth", leak.entityCountGrowth()).append(',');
        field(json, "blockEntityCountGrowth", leak.blockEntityCountGrowth()).append(',');
        field(json, "loadedChunkCountGrowth", leak.loadedChunkCountGrowth());
        json.append('}');
        return json;
    }

    private static StringBuilder config(StringBuilder json, BenchConfig config) {
        json.append('{');
        field(json, "scenario", config.scenario()).append(',');
        field(json, "seed", config.seed()).append(',');
        field(json, "warmupTicks", config.warmupTicks()).append(',');
        field(json, "measureTicks", config.measureTicks());
        json.append('}');
        return json;
    }

    private static StringBuilder rng(StringBuilder json, RngInfo rng) {
        json.append('{');
        field(json, "seed", rng.seed()).append(',');
        field(json, "dimension", rng.dimension()).append(',');
        field(json, "fullyPinned", rng.fullyPinned());
        json.append('}');
        return json;
    }

    private static StringBuilder statsSet(StringBuilder json, StatsSet set) {
        json.append('{');
        json.append("\"clean\":");
        statsBlock(json, set.clean()).append(',');
        json.append("\"raw\":");
        statsBlock(json, set.raw());
        json.append('}');
        return json;
    }

    private static StringBuilder statsBlock(StringBuilder json, StatsBlock stats) {
        json.append('{');
        field(json, "count", stats.count()).append(',');
        field(json, "minNanos", stats.minNanos()).append(',');
        field(json, "medianNanos", stats.medianNanos()).append(',');
        field(json, "p50Nanos", stats.p50Nanos()).append(',');
        field(json, "p90Nanos", stats.p90Nanos()).append(',');
        field(json, "p95Nanos", stats.p95Nanos()).append(',');
        field(json, "p99Nanos", stats.p99Nanos()).append(',');
        field(json, "maxNanos", stats.maxNanos()).append(',');
        field(json, "meanNanos", stats.meanNanos()).append(',');
        field(json, "stddevNanos", stats.stddevNanos()).append(',');
        field(json, "madNanos", stats.madNanos());
        json.append('}');
        return json;
    }

    private static StringBuilder gc(StringBuilder json, GcSummary gc) {
        json.append('{');
        field(json, "events", gc.events()).append(',');
        field(json, "totalPauseMs", gc.totalPauseMs()).append(',');
        field(json, "contaminatedTicks", gc.contaminatedTicks());
        json.append('}');
        return json;
    }

    private static StringBuilder alloc(StringBuilder json, AllocationSummary alloc) {
        json.append('{');
        field(json, "enabled", alloc.enabled()).append(',');
        field(json, "bytesTotal", alloc.bytesTotal()).append(',');
        field(json, "bytesPerTickMedian", alloc.bytesPerTickMedian());
        json.append('}');
        return json;
    }

    private static StringBuilder resourceSummary(StringBuilder json, ResourceTimeline resources) {
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, ResourceMetricSummary> entry : resources.summary().metrics().entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            quoted(json, entry.getKey()).append(':');
            resourceMetric(json, entry.getValue());
        }
        json.append('}');
        return json;
    }

    private static StringBuilder resourceMetric(StringBuilder json, ResourceMetricSummary metric) {
        json.append('{');
        field(json, "count", metric.count()).append(',');
        field(json, "min", metric.min()).append(',');
        field(json, "median", metric.median()).append(',');
        field(json, "p95", metric.p95()).append(',');
        field(json, "max", metric.max()).append(',');
        field(json, "slopePerSecond", metric.slopePerSecond());
        json.append('}');
        return json;
    }

    private static StringBuilder resourceTimeline(StringBuilder json, ResourceTimeline resources) {
        json.append('[');
        boolean first = true;
        for (ResourceSample sample : resources.samples()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            resourceSample(json, sample);
        }
        json.append(']');
        return json;
    }

    private static StringBuilder resourceSample(StringBuilder json, ResourceSample sample) {
        json.append('{');
        field(json, "nanoTime", sample.nanoTime()).append(',');
        field(json, "processCpuLoad", sample.processCpuLoad()).append(',');
        field(json, "systemLoadAverage", sample.systemLoadAverage()).append(',');
        json.append("\"perCoreUtilization\":");
        doubleArray(json, sample.perCoreUtilization()).append(',');
        field(json, "processCpuTimeNanos", sample.processCpuTimeNanos()).append(',');
        field(json, "serverThreadCpuTimeNanos", sample.serverThreadCpuTimeNanos()).append(',');
        field(json, "workerThreadCpuTimeNanos", sample.workerThreadCpuTimeNanos()).append(',');
        field(json, "heapUsedBytes", sample.heapUsedBytes()).append(',');
        field(json, "nonHeapUsedBytes", sample.nonHeapUsedBytes()).append(',');
        field(json, "postGcLiveSetBytes", sample.postGcLiveSetBytes()).append(',');
        field(json, "diskReadBytes", sample.diskReadBytes()).append(',');
        field(json, "diskWriteBytes", sample.diskWriteBytes()).append(',');
        field(json, "allocationRateBytesPerSecond", sample.allocationRateBytesPerSecond()).append(',');
        field(json, "allocatedBytesTotal", sample.allocatedBytesTotal());
        json.append('}');
        return json;
    }

    private static StringBuilder stringArray(StringBuilder json, Iterable<String> values) {
        json.append('[');
        boolean first = true;
        for (String value : values) {
            if (!first) {
                json.append(',');
            }
            first = false;
            quoted(json, value);
        }
        json.append(']');
        return json;
    }

    private static StringBuilder doubleArray(StringBuilder json, Iterable<Double> values) {
        json.append('[');
        boolean first = true;
        for (Double value : values) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(safeDouble(value == null ? -1.0 : value));
        }
        json.append(']');
        return json;
    }

    private static StringBuilder doubleMap(StringBuilder json, Map<String, Double> values) {
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            quoted(json, entry.getKey()).append(':').append(safeDouble(entry.getValue()));
        }
        json.append('}');
        return json;
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        quoted(json, name).append(':');
        quoted(json, value);
        return json;
    }

    private static StringBuilder field(StringBuilder json, String name, long value) {
        quoted(json, name).append(':').append(value);
        return json;
    }

    private static StringBuilder field(StringBuilder json, String name, int value) {
        quoted(json, name).append(':').append(value);
        return json;
    }

    private static StringBuilder field(StringBuilder json, String name, double value) {
        quoted(json, name).append(':').append(safeDouble(value));
        return json;
    }

    private static StringBuilder field(StringBuilder json, String name, boolean value) {
        quoted(json, name).append(':').append(value);
        return json;
    }

    private static StringBuilder quoted(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
        return json;
    }

    private static String safeDouble(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "-1.0";
    }
}
