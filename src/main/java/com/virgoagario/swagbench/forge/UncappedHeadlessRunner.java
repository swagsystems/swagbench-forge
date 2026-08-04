package com.virgoagario.swagbench.forge;

import com.mojang.logging.LogUtils;
import com.virgoagario.swagbench.analysis.BottleneckAttributor;
import com.virgoagario.swagbench.analysis.BottleneckReport;
import com.virgoagario.swagbench.analysis.LeakDetector;
import com.virgoagario.swagbench.analysis.LeakReport;
import com.virgoagario.swagbench.core.AllocationSummary;
import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.ExitCodes;
import com.virgoagario.swagbench.core.GcEvent;
import com.virgoagario.swagbench.core.Report;
import com.virgoagario.swagbench.core.ReportWriter;
import com.virgoagario.swagbench.core.RngInfo;
import com.virgoagario.swagbench.core.StatsEngine;
import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.core.TickSample;
import com.virgoagario.swagbench.load.LoadController;
import com.virgoagario.swagbench.load.LoadSummary;
import com.virgoagario.swagbench.network.NetworkProbe;
import com.virgoagario.swagbench.network.NetworkSummary;
import com.virgoagario.swagbench.probe.BucketRecorder;
import com.virgoagario.swagbench.probe.SubsystemProbes;
import com.virgoagario.swagbench.resource.ResourceSampler;
import com.virgoagario.swagbench.resource.ResourceSamplerConfig;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import com.virgoagario.swagbench.scenario.RngHarness;
import com.virgoagario.swagbench.scenario.ScenarioLoader;
import com.virgoagario.swagbench.scenario.ScenarioSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

public final class UncappedHeadlessRunner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final StatsEngine statsEngine;
    private final ReportWriter reportWriter;
    private final ScenarioLoader scenarioLoader;
    private final RngHarness rngHarness;

    public UncappedHeadlessRunner(StatsEngine statsEngine) {
        this(statsEngine, new ReportWriter(), new ScenarioLoader(), new RngHarness());
    }

    UncappedHeadlessRunner(
            StatsEngine statsEngine,
            ReportWriter reportWriter,
            ScenarioLoader scenarioLoader,
            RngHarness rngHarness) {
        this.statsEngine = statsEngine;
        this.reportWriter = reportWriter;
        this.scenarioLoader = scenarioLoader;
        this.rngHarness = rngHarness;
    }

    public int run(MinecraftServer server, BenchConfig config, Path outputDir) {
        return run(server, config, outputDir, ResourceSamplerConfig.disabled());
    }

    public int run(MinecraftServer server, BenchConfig config, Path outputDir, ResourceSamplerConfig resourceConfig) {
        try {
            disableBackgroundWorldActivity(server);
            var level = BenchmarkLevel.level(server);
            RngInfo rngInfo = rngHarness.seedAll(config.seed(), level);
            ScenarioSpec scenarioSpec = ScenarioSpec.from(config);
            scenarioLoader.load(scenarioSpec, level);

            for (int i = 0; i < config.warmupTicks(); i++) {
                scenarioLoader.runTickWorkload(scenarioSpec, i);
                server.tickServer(() -> true);
            }
            scenarioLoader.reprimeDynamicState(scenarioSpec, level);
            GcSettler.settleAfterWarmup();

            BucketRecorder recorder = new BucketRecorder();
            List<TickSample> samples = new ArrayList<>(config.measureTicks());
            try (GcMonitor gcMonitor = new GcMonitor()) {
                ResourceSampler resourceSampler = ResourceSampler.start(resourceConfig, gcMonitor);
                SubsystemProbes.beginMeasurement(recorder);
                try {
                    for (int i = 0; i < config.measureTicks(); i++) {
                        recorder.drain();
                        long start = System.nanoTime();
                        scenarioLoader.runTickWorkload(scenarioSpec, i);
                        server.tickServer(() -> true);
                        long end = System.nanoTime();
                        Map<SubsystemBucket, Long> buckets = recorder.drain();
                        samples.add(TickSample.of(i, start, end, buckets));
                    }
                } finally {
                    resourceSampler.close();
                }
                SubsystemProbes.endMeasurement();

                ResourceTimeline resources = resourceConfig.enabled() ? resourceSampler.timeline() : null;
                AllocationSummary alloc = resourceConfig.enabled()
                        ? resourceSampler.allocationSummary(config.measureTicks())
                        : AllocationSummary.disabled();
                Report report = statsEngine.compute(
                        config,
                        samples,
                        gcMonitor.drain(),
                        EnvironmentCollector.collect(),
                        rngInfo,
                        alloc)
                        .withResources(resources, alloc);
                reportWriter.write(report, outputDir);
                return report.degraded() ? ExitCodes.DEGRADED : ExitCodes.OK;
            } finally {
                SubsystemProbes.endMeasurement();
            }
        } catch (Exception e) {
            LOGGER.error("SwagBench headless run failed", e);
            return ExitCodes.SETUP_FAILURE;
        }
    }

    private static void disableBackgroundWorldActivity(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        }
    }

    int runRepeated(MinecraftServer server, HeadlessRunRequest request, Path outputDir) {
        if ("realistic".equals(request.mode())) {
            return runRealistic(server, request, outputDir);
        }
        boolean sawDegraded = false;
        for (int i = 0; i < request.runs(); i++) {
            int exitCode = run(server, request.config(), outputDir, request.resourceSampler());
            if (exitCode == ExitCodes.SETUP_FAILURE) {
                return ExitCodes.SETUP_FAILURE;
            }
            if (exitCode == ExitCodes.DEGRADED) {
                sawDegraded = true;
            }
        }
        return sawDegraded ? ExitCodes.DEGRADED : ExitCodes.OK;
    }

    private int runRealistic(MinecraftServer server, HeadlessRunRequest request, Path outputDir) {
        BenchConfig config = request.config();
        try {
            var level = BenchmarkLevel.level(server);
            RngInfo rngInfo = rngHarness.seedAll(config.seed(), level);
            ScenarioSpec scenarioSpec = ScenarioSpec.from(config);
            ScenarioLoader.LoadedScenario loadedScenario = scenarioLoader.load(scenarioSpec, level);

            for (int i = 0; i < config.warmupTicks(); i++) {
                scenarioLoader.runTickWorkload(scenarioSpec, i);
                server.tickServer(() -> true);
            }
            scenarioLoader.reprimeDynamicState(scenarioSpec, level);
            GcSettler.settleAfterWarmup();

            BucketRecorder recorder = new BucketRecorder();
            List<TickSample> samples = new ArrayList<>(config.measureTicks());
            LoadSummary loadSummary;
            NetworkSummary networkSummary;
            LeakCountSnapshot startCounts = LeakCountSnapshot.capture(level);
            try (GcMonitor gcMonitor = new GcMonitor();
                    ResourceSampler resourceSampler = ResourceSampler.start(request.resourceSampler(), gcMonitor);
                    LoadController loadController = new LoadController(request.load(), loadedScenario.clientAnchors())) {
                NetworkProbe.begin();
                boolean loadReady = false;
                boolean loadDropped = false;
                try {
                    long loadDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45L);
                    int loadTick = 0;
                    while (System.nanoTime() < loadDeadline && !loadController.summary().reachedTarget()) {
                        recorder.drain();
                        scenarioLoader.runTickWorkload(scenarioSpec, loadTick);
                        loadController.tick(server, level, loadTick);
                        server.tickServer(() -> true);
                        recorder.drain();
                        loadTick++;
                    }
                    loadReady = loadController.summary().reachedTarget();
                    SubsystemProbes.beginMeasurement(recorder);
                    for (int i = 0; i < config.measureTicks(); i++) {
                        long start = System.nanoTime();
                        scenarioLoader.runTickWorkload(scenarioSpec, i);
                        loadController.tick(server, level, loadTick + i);
                        server.tickServer(() -> true);
                        long end = System.nanoTime();
                        Map<SubsystemBucket, Long> buckets = recorder.drain();
                        samples.add(TickSample.of(i, start, end, buckets));
                        if (!loadController.summary().reachedTarget()) {
                            loadDropped = true;
                        }
                    }
                } finally {
                    SubsystemProbes.endMeasurement();
                    networkSummary = NetworkProbe.snapshot();
                    NetworkProbe.end();
                }
                loadSummary = loadController.summary();
                AllocationSummary alloc = resourceSampler.allocationSummary(config.measureTicks());
                ResourceTimeline resourceTimeline = resourceSampler.timeline();
                List<GcEvent> gcEvents = gcMonitor.drain();
                BottleneckReport bottleneck = new BottleneckAttributor()
                        .analyze(samples, resourceTimeline, networkSummary, gcPausePercent(samples, gcEvents));
                LeakCountSnapshot endCounts = LeakCountSnapshot.capture(level);
                LeakReport leak = new LeakDetector().analyze(
                        resourceTimeline,
                        startCounts.entityCount(),
                        endCounts.entityCount(),
                        startCounts.blockEntityCount(),
                        endCounts.blockEntityCount(),
                        startCounts.loadedChunkCount(),
                        endCounts.loadedChunkCount());
                Report report = statsEngine.compute(
                        config,
                        samples,
                        gcEvents,
                        EnvironmentCollector.collect(),
                        rngInfo,
                        alloc)
                        .withMode("realistic")
                        .withDegradation(false, "")
                        .withResources(resourceTimeline, alloc)
                        .withLoad(loadSummary)
                        .withNetwork(networkSummary)
                        .withAnalyses(bottleneck, leak);
                if (!loadReady) {
                    report = report.withDegradation(true, "realistic_load_target_not_reached");
                } else if (loadDropped) {
                    report = report.withDegradation(true, "realistic_load_target_dropped");
                }
                reportWriter.write(report, outputDir);
                return report.degraded() ? ExitCodes.DEGRADED : ExitCodes.OK;
            } finally {
                SubsystemProbes.endMeasurement();
                NetworkProbe.end();
            }
        } catch (Exception e) {
            LOGGER.error("SwagBench realistic run failed", e);
            return ExitCodes.SETUP_FAILURE;
        }
    }

    private static double gcPausePercent(List<TickSample> samples, List<GcEvent> gcEvents) {
        long sampleNanos = samples.stream().mapToLong(TickSample::durationNanos).sum();
        long gcNanos = gcEvents.stream().mapToLong(GcEvent::durationNanos).sum();
        if (sampleNanos <= 0L) {
            return 0.0;
        }
        return 100.0 * gcNanos / sampleNanos;
    }

    private record LeakCountSnapshot(long entityCount, long blockEntityCount, long loadedChunkCount) {
        static LeakCountSnapshot capture(ServerLevel level) {
            return new LeakCountSnapshot(countEntities(level), -1L, level.getChunkSource().getLoadedChunksCount());
        }

        private static long countEntities(ServerLevel level) {
            long count = 0L;
            for (var ignored : level.getAllEntities()) {
                count++;
            }
            return count;
        }
    }
}
