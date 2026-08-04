package com.virgoagario.swagbench.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.ExitCodes;
import com.virgoagario.swagbench.core.Report;
import com.virgoagario.swagbench.core.StatsEngine;
import com.virgoagario.swagbench.scenario.SyntheticScenarioRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesReportAndReturnsOkForCleanTinyRun() throws Exception {
        BenchController controller = BenchController.synthetic(new StatsEngine(0.25, 1_000_000));

        RunResult result = controller.start(BenchConfig.tiny("mixed-v1", 123L, 2, 8), tempDir);

        assertEquals(ExitCodes.OK, result.exitCode());
        assertEquals(RunState.IDLE, controller.state());
        assertNotNull(result.report());
        assertTrue(Files.exists(result.reportPath()));
        assertEquals("mixed-v1", result.report().config().scenario());
        assertEquals(8, result.report().tick().raw().count());
    }

    @Test
    void refusesConcurrentRun() throws Exception {
        CountDownLatch enteredRunner = new CountDownLatch(1);
        CountDownLatch releaseRunner = new CountDownLatch(1);
        BenchController controller = new BenchController(
                config -> {
                    enteredRunner.countDown();
                    assertTrue(releaseRunner.await(5, TimeUnit.SECONDS));
                    return SyntheticScenarioRunner.mixedV1().run(config);
                },
                new StatsEngine(0.25, 1_000_000));

        Thread first = new Thread(() -> controller.start(BenchConfig.tiny("mixed-v1", 123L, 1, 4), tempDir));
        first.start();
        assertTrue(enteredRunner.await(5, TimeUnit.SECONDS));

        RunResult second = controller.start(BenchConfig.tiny("mixed-v1", 123L, 1, 4), tempDir);
        releaseRunner.countDown();
        first.join(5_000);

        assertEquals(ExitCodes.SETUP_FAILURE, second.exitCode());
        assertTrue(second.message().contains("already running"));
    }

    @Test
    void abortsRunningCoreRunBeforeReportIsWritten() throws Exception {
        CountDownLatch enteredRunner = new CountDownLatch(1);
        CountDownLatch releaseRunner = new CountDownLatch(1);
        BenchController controller = new BenchController(
                config -> {
                    enteredRunner.countDown();
                    assertTrue(releaseRunner.await(5, TimeUnit.SECONDS));
                    return SyntheticScenarioRunner.mixedV1().run(config);
                },
                new StatsEngine(0.25, 1_000_000));
        AtomicReference<RunResult> result = new AtomicReference<>();

        Thread first = new Thread(() -> result.set(controller.start(BenchConfig.tiny("mixed-v1", 123L, 1, 4), tempDir)));
        first.start();
        assertTrue(enteredRunner.await(5, TimeUnit.SECONDS));

        assertTrue(controller.abort());
        assertEquals(RunState.IDLE, controller.state());
        assertFalse(controller.abort());
        releaseRunner.countDown();
        first.join(5_000);

        assertEquals(ExitCodes.SETUP_FAILURE, result.get().exitCode());
        assertTrue(result.get().message().contains("aborted"));
        assertNull(result.get().reportPath());
        assertEquals(0L, Files.list(tempDir).count());
    }

    @Test
    void returnsSetupFailureWhenScenarioFailsBeforeReport() {
        BenchController controller = new BenchController(
                config -> {
                    throw new IllegalStateException("scenario exploded");
                },
                new StatsEngine(0.25, 1_000_000));

        RunResult result = controller.start(BenchConfig.tiny("mixed-v1", 123L, 1, 4), tempDir);

        assertEquals(ExitCodes.SETUP_FAILURE, result.exitCode());
        assertTrue(result.message().contains("scenario exploded"));
    }

    @Test
    void returnsDistinctExitCodeForDegradedReport() {
        BenchController controller = new BenchController(
                SyntheticScenarioRunner.gcContaminated(),
                new StatsEngine(0.25, 1_000_000));

        RunResult result = controller.start(BenchConfig.tiny("mixed-v1", 123L, 1, 4), tempDir);

        assertEquals(ExitCodes.DEGRADED, result.exitCode());
        assertTrue(result.report().degraded());
        assertTrue(Files.exists(result.reportPath()));
    }

    @Test
    void repeatedSyntheticMixedV1RunsHaveZeroMedianDeltaAndLowMad() {
        BenchController controller = BenchController.synthetic(new StatsEngine(0.25, 1_000_000));

        Report first = controller.start(BenchConfig.tiny("mixed-v1", 99L, 5, 40), tempDir).report();
        Report second = controller.start(BenchConfig.tiny("mixed-v1", 99L, 5, 40), tempDir).report();

        assertEquals(first.tick().clean().medianNanos(), second.tick().clean().medianNanos());
        assertTrue(first.tick().clean().madNanos() <= 250.0);
        assertTrue(second.tick().clean().madNanos() <= 250.0);
    }
}
