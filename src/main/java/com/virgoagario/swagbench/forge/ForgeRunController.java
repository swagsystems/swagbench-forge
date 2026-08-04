package com.virgoagario.swagbench.forge;

import com.mojang.logging.LogUtils;
import com.virgoagario.swagbench.control.RunState;
import com.virgoagario.swagbench.core.AllocationSummary;
import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.ExitCodes;
import com.virgoagario.swagbench.core.Report;
import com.virgoagario.swagbench.core.ReportWriter;
import com.virgoagario.swagbench.core.RngInfo;
import com.virgoagario.swagbench.core.StatsEngine;
import com.virgoagario.swagbench.core.TickSample;
import com.virgoagario.swagbench.scenario.RngHarness;
import com.virgoagario.swagbench.scenario.ScenarioLoader;
import com.virgoagario.swagbench.scenario.ScenarioSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

public final class ForgeRunController {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final StatsEngine statsEngine;
    private final ReportWriter reportWriter;
    private final TickProbe tickProbe;
    private final ScenarioLoader scenarioLoader = new ScenarioLoader();
    private final RngHarness rngHarness = new RngHarness();
    private RunState state = RunState.IDLE;
    private BenchConfig config;
    private Path outputDir;
    private int warmupRemaining;
    private int measureRemaining;
    private final List<TickSample> samples = new ArrayList<>();
    private GcMonitor gcMonitor;
    private RngInfo rngInfo;
    private MinecraftServer serverForSetup;
    private boolean haltServerOnFinish;
    private boolean exitJvmOnFinish;

    public ForgeRunController(StatsEngine statsEngine) {
        this(statsEngine, new ReportWriter(), new TickProbe());
    }

    ForgeRunController(StatsEngine statsEngine, ReportWriter reportWriter, TickProbe tickProbe) {
        this.statsEngine = statsEngine;
        this.reportWriter = reportWriter;
        this.tickProbe = tickProbe;
    }

    public synchronized RunState state() {
        return state;
    }

    public synchronized boolean abort() {
        if (state != RunState.RUNNING) {
            return false;
        }
        resetAfterRun();
        return true;
    }

    public synchronized boolean requestRun(BenchConfig requestedConfig, Path requestedOutputDir, MinecraftServer serverToStop) {
        return requestRun(requestedConfig, requestedOutputDir, serverToStop, false, serverToStop != null);
    }

    public synchronized boolean requestRun(
            BenchConfig requestedConfig,
            Path requestedOutputDir,
            MinecraftServer server,
            boolean requestedExitJvm,
            boolean requestedHaltServer) {
        if (state != RunState.IDLE) {
            return false;
        }
        ScenarioSpec requestedScenarioSpec = ScenarioSpec.from(requestedConfig);
        // TODO(Section 12): replace vanilla-paced event collection with the approved
        // uncapped loop owner once that design question is resolved.
        state = RunState.RUNNING;
        config = requestedConfig;
        outputDir = requestedOutputDir;
        warmupRemaining = requestedConfig.warmupTicks();
        measureRemaining = requestedConfig.measureTicks();
        samples.clear();
        serverForSetup = server;
        haltServerOnFinish = requestedHaltServer;
        exitJvmOnFinish = requestedExitJvm;
        if (serverForSetup != null) {
            try {
                setupScenario(serverForSetup, requestedScenarioSpec);
            } catch (RuntimeException e) {
                resetAfterRun();
                throw e;
            }
        }
        if (warmupRemaining == 0) {
            beginMeasurementWindow();
        }
        return true;
    }

    public synchronized void onServerTick(TickEvent.ServerTickEvent event) {
        if (state != RunState.RUNNING) {
            return;
        }
        if (warmupRemaining > 0) {
            if (event.phase == TickEvent.Phase.END && --warmupRemaining == 0) {
                beginMeasurementWindow();
            }
            return;
        }

        tickProbe.onServerTick(event);
        if (event.phase == TickEvent.Phase.END) {
            tickProbe.drainLastSample().ifPresent(samples::add);
            measureRemaining--;
            if (measureRemaining <= 0) {
                finish();
            }
        }
    }

    private void finish() {
        tickProbe.endMeasurement();
        List<com.virgoagario.swagbench.core.GcEvent> gcEvents = gcMonitor == null ? List.of() : gcMonitor.drain();
        if (gcMonitor != null) {
            gcMonitor.close();
        }
        int exitCode = ExitCodes.OK;
        try {
            Report report = statsEngine.compute(
                    config,
                    samples,
                    gcEvents,
                    EnvironmentCollector.collect(),
                    rngInfo,
                    AllocationSummary.disabled());
            exitCode = report.degraded() ? ExitCodes.DEGRADED : ExitCodes.OK;
            Path path = reportWriter.write(report, outputDir);
            LOGGER.info("SwagBench wrote {}", path);
        } catch (Exception e) {
            exitCode = ExitCodes.SETUP_FAILURE;
            LOGGER.error("SwagBench failed to write report", e);
        } finally {
            MinecraftServer serverToHalt = serverForSetup;
            boolean shouldHalt = haltServerOnFinish;
            boolean shouldExit = exitJvmOnFinish;
            resetAfterRun();
            if (shouldHalt && serverToHalt != null) {
                serverToHalt.halt(false);
            }
            if (shouldExit) {
                System.exit(exitCode);
            }
        }
    }

    private void beginMeasurementWindow() {
        if (serverForSetup != null) {
            scenarioLoader.reprimeDynamicState(ScenarioSpec.from(config), BenchmarkLevel.level(serverForSetup));
        }
        GcSettler.settleAfterWarmup();
        gcMonitor = new GcMonitor();
        tickProbe.beginMeasurement();
    }

    private void resetAfterRun() {
        tickProbe.endMeasurement();
        if (gcMonitor != null) {
            gcMonitor.close();
            gcMonitor = null;
        }
        rngInfo = null;
        state = RunState.IDLE;
        config = null;
        outputDir = null;
        warmupRemaining = 0;
        measureRemaining = 0;
        samples.clear();
        serverForSetup = null;
        haltServerOnFinish = false;
        exitJvmOnFinish = false;
    }

    private void setupScenario(MinecraftServer server, ScenarioSpec scenarioSpec) {
        var level = BenchmarkLevel.level(server);
        rngInfo = rngHarness.seedAll(scenarioSpec.seed(), level);
        scenarioLoader.load(scenarioSpec, level);
    }
}
