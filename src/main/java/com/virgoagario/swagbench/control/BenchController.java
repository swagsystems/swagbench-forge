package com.virgoagario.swagbench.control;

import com.virgoagario.swagbench.core.AllocationSummary;
import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.EnvironmentInfo;
import com.virgoagario.swagbench.core.ExitCodes;
import com.virgoagario.swagbench.core.Report;
import com.virgoagario.swagbench.core.ReportWriter;
import com.virgoagario.swagbench.core.StatsEngine;
import com.virgoagario.swagbench.scenario.ScenarioResult;
import com.virgoagario.swagbench.scenario.ScenarioRunner;
import com.virgoagario.swagbench.scenario.SyntheticScenarioRunner;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class BenchController {
    private final ScenarioRunner scenarioRunner;
    private final StatsEngine statsEngine;
    private final ReportWriter reportWriter;
    private final AtomicReference<RunState> state = new AtomicReference<>(RunState.IDLE);

    public BenchController(ScenarioRunner scenarioRunner, StatsEngine statsEngine) {
        this(scenarioRunner, statsEngine, new ReportWriter());
    }

    public BenchController(ScenarioRunner scenarioRunner, StatsEngine statsEngine, ReportWriter reportWriter) {
        this.scenarioRunner = Objects.requireNonNull(scenarioRunner, "scenarioRunner");
        this.statsEngine = Objects.requireNonNull(statsEngine, "statsEngine");
        this.reportWriter = Objects.requireNonNull(reportWriter, "reportWriter");
    }

    public static BenchController synthetic(StatsEngine statsEngine) {
        return new BenchController(SyntheticScenarioRunner.mixedV1(), statsEngine);
    }

    public RunState state() {
        return state.get();
    }

    public boolean abort() {
        return state.compareAndSet(RunState.RUNNING, RunState.IDLE);
    }

    public RunResult start(BenchConfig config, Path outputDir) {
        if (!state.compareAndSet(RunState.IDLE, RunState.RUNNING)) {
            return RunResult.failure(ExitCodes.SETUP_FAILURE, "swagbench run already running");
        }

        try {
            ScenarioResult scenario = scenarioRunner.run(config);
            if (state.get() != RunState.RUNNING) {
                return RunResult.failure(ExitCodes.SETUP_FAILURE, "swagbench run aborted");
            }
            Report report = statsEngine.compute(
                    config,
                    scenario.samples(),
                    scenario.gcEvents(),
                    EnvironmentInfo.synthetic(),
                    AllocationSummary.disabled());
            Path reportPath = reportWriter.write(report, outputDir);
            int exitCode = report.degraded() ? ExitCodes.DEGRADED : ExitCodes.OK;
            String message = report.degraded() ? report.degradedReason() : "ok";
            return new RunResult(exitCode, report, reportPath, message);
        } catch (Exception e) {
            return RunResult.failure(ExitCodes.SETUP_FAILURE, e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            state.set(RunState.IDLE);
        }
    }
}
