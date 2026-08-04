package com.virgoagario.swagbench.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virgoagario.swagbench.analysis.BottleneckReport;
import com.virgoagario.swagbench.analysis.BottleneckWindow;
import com.virgoagario.swagbench.analysis.LeakReport;
import com.virgoagario.swagbench.resource.ResourceSample;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import com.virgoagario.swagbench.load.LoadSummary;
import com.virgoagario.swagbench.network.NetworkConnectionSummary;
import com.virgoagario.swagbench.network.NetworkSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesStableSchemaWithExplicitNanosecondStatFields() throws Exception {
        Report report = new StatsEngine(0.25, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 7L, 1, 1),
                List.of(TickSample.of(0, 0, 123, Map.of(SubsystemBucket.ENTITIES, 23L))),
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());

        Path written = new ReportWriter().write(report, tempDir);
        String json = Files.readString(written);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(written.getFileName().toString().startsWith("swagbench-"));
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals("marginal", root.get("mode").getAsString());
        assertTrue(root.get("runId").isJsonPrimitive());
        assertTrue(root.get("timestampUtc").isJsonPrimitive());
        assertTrue(root.get("env").isJsonObject());
        assertEquals("mixed-v1", root.getAsJsonObject("config").get("scenario").getAsString());
        assertEquals(7L, root.getAsJsonObject("rng").get("seed").getAsLong());
        assertTrue(root.getAsJsonObject("rng").get("dimension").isJsonPrimitive());
        assertFalse(root.getAsJsonObject("rng").get("fullyPinned").getAsBoolean());
        assertStatsSet(root.getAsJsonObject("tick"));
        assertStatsSet(root.getAsJsonObject("tickWindows"));
        assertTrue(root.getAsJsonObject("subsystems").get("entities").isJsonObject());
        assertTrue(root.getAsJsonObject("subsystems").get("other").isJsonObject());
        assertTrue(root.getAsJsonObject("gc").get("events").isJsonPrimitive());
        assertFalse(root.getAsJsonObject("alloc").get("enabled").getAsBoolean());
        assertTrue(root.getAsJsonObject("env").get("systemLoadAverage").isJsonPrimitive());
        assertTrue(root.getAsJsonObject("env").get("cgroupSwapCurrent").isJsonPrimitive());
        assertFalse(root.get("degraded").getAsBoolean());
        assertFalse(root.has("runs"));
        assertFalse(root.has("runIndex"));
        assertFalse(root.has("resourceSummary"));
        assertFalse(root.has("resourceTimeline"));
        assertFalse(root.has("load"));
        assertFalse(root.has("network"));
        assertFalse(root.has("bottleneck"));
        assertFalse(root.has("leak"));
    }

    @Test
    void writesOptionalResourceBlocksOnlyWhenReportCarriesTimeline() {
        Report baseReport = new StatsEngine(0.25, 1_000_000).compute(
                BenchConfig.tiny("mixed-v1", 7L, 1, 1),
                List.of(TickSample.of(0, 0, 123, Map.of(SubsystemBucket.ENTITIES, 23L))),
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());
        ResourceTimeline timeline = ResourceTimeline.of(List.of(new ResourceSample(
                1_000L,
                0.5,
                0.25,
                List.of(0.25, 0.75),
                10_000L,
                5_000L,
                2_000L,
                512L,
                64L,
                480L,
                128L,
                256L,
                1_024L,
                2_048L)));

        String json = new ReportWriter().toJson(
                baseReport.withResources(timeline, new AllocationSummary(true, 4_096L, 2_048L)));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(root.getAsJsonObject("alloc").get("enabled").getAsBoolean());
        assertEquals(4_096L, root.getAsJsonObject("alloc").get("bytesTotal").getAsLong());
        assertTrue(root.get("resourceSummary").isJsonObject());
        assertTrue(root.get("resourceTimeline").isJsonArray());
        JsonObject firstSample = root.getAsJsonArray("resourceTimeline").get(0).getAsJsonObject();
        assertEquals(1_000L, firstSample.get("nanoTime").getAsLong());
        assertEquals(0.5, firstSample.get("processCpuLoad").getAsDouble(), 0.0001);
        assertEquals(512L, firstSample.get("heapUsedBytes").getAsLong());
    }

    @Test
    void writesOptionalRealisticLoadAndNetworkBlocks() {
        Report baseReport = new StatsEngine(0.25, 1_000_000).compute(
                BenchConfig.tiny("populated-v1", 7L, 1, 1),
                List.of(TickSample.of(0, 0, 123, Map.of(SubsystemBucket.ENTITIES, 23L))),
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());
        LoadSummary load = new LoadSummary("realistic", 3, 3, 0, 2, 5, 4, 7L, true);
        NetworkSummary network = new NetworkSummary(
                1,
                1234L,
                5678L,
                List.of(new NetworkConnectionSummary("swagbench-0", 1234L, 5678L)));

        JsonObject root = JsonParser.parseString(new ReportWriter().toJson(
                baseReport.withMode("realistic").withLoad(load).withNetwork(network))).getAsJsonObject();

        assertEquals("realistic", root.get("mode").getAsString());
        assertEquals(3, root.getAsJsonObject("load").get("targetClients").getAsInt());
        assertTrue(root.getAsJsonObject("load").get("reachedTarget").getAsBoolean());
        assertEquals(1234L, root.getAsJsonObject("network").get("bytesIn").getAsLong());
        assertEquals(5678L, root.getAsJsonObject("network").get("bytesOut").getAsLong());
        assertEquals("swagbench-0", root.getAsJsonObject("network")
                .getAsJsonArray("connections")
                .get(0)
                .getAsJsonObject()
                .get("id")
                .getAsString());
    }

    @Test
    void writesOptionalRealisticAnalysisBlocks() {
        Report baseReport = new StatsEngine(0.25, 1_000_000).compute(
                BenchConfig.tiny("populated-v1", 7L, 1, 1),
                List.of(TickSample.of(0, 0, 123, Map.of(SubsystemBucket.ENTITIES, 23L))),
                List.of(),
                EnvironmentInfo.synthetic(),
                AllocationSummary.disabled());
        BottleneckReport bottleneck = new BottleneckReport(
                "cpu",
                List.of(new BottleneckWindow(
                        0,
                        99,
                        75_000_000L,
                        "cpu",
                        "entities",
                        Map.of("maxCoreUtilization", 0.99, "gcPausePercent", 0.0))));
        LeakReport leak = new LeakReport(true, 12_000_000.0, 0.95, 4, false, 0, 0, 0);

        JsonObject root = JsonParser.parseString(new ReportWriter().toJson(
                baseReport.withMode("realistic").withAnalyses(bottleneck, leak))).getAsJsonObject();

        assertEquals("cpu", root.getAsJsonObject("bottleneck").get("overall").getAsString());
        assertEquals("cpu", root.getAsJsonObject("bottleneck")
                .getAsJsonArray("windows")
                .get(0)
                .getAsJsonObject()
                .get("topRank")
                .getAsString());
        assertTrue(root.getAsJsonObject("leak").get("leakSuspected").getAsBoolean());
        assertEquals(12_000_000.0, root.getAsJsonObject("leak").get("postGcSlopeBytesPerMinute").getAsDouble(), 0.1);
    }

    private static void assertStatsSet(JsonObject statsSet) {
        assertStatsBlock(statsSet.getAsJsonObject("clean"));
        assertStatsBlock(statsSet.getAsJsonObject("raw"));
    }

    private static void assertStatsBlock(JsonObject stats) {
        assertTrue(stats.get("count").isJsonPrimitive());
        assertTrue(stats.get("minNanos").isJsonPrimitive());
        assertTrue(stats.get("medianNanos").isJsonPrimitive());
        assertTrue(stats.get("p50Nanos").isJsonPrimitive());
        assertTrue(stats.get("p90Nanos").isJsonPrimitive());
        assertTrue(stats.get("p95Nanos").isJsonPrimitive());
        assertTrue(stats.get("p99Nanos").isJsonPrimitive());
        assertTrue(stats.get("maxNanos").isJsonPrimitive());
        assertTrue(stats.get("meanNanos").isJsonPrimitive());
        assertTrue(stats.get("stddevNanos").isJsonPrimitive());
        assertTrue(stats.get("madNanos").isJsonPrimitive());
    }
}
