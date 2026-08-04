package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GcSettleBoundarySourceShapeTest {
    @Test
    void headlessRunnerSettlesGcAfterWarmupBeforeMeasurementStarts() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/UncappedHeadlessRunner.java"));

        int reprime = source.indexOf("scenarioLoader.reprimeDynamicState(scenarioSpec, level)");
        int settle = source.indexOf("GcSettler.settleAfterWarmup()");
        int monitor = source.indexOf("new GcMonitor()");
        int begin = source.indexOf("SubsystemProbes.beginMeasurement");
        int tickStart = source.indexOf("long start = System.nanoTime()");
        int tickWorkload = source.indexOf("scenarioLoader.runTickWorkload(scenarioSpec, i)", tickStart);

        assertTrue(reprime > 0, "headless runner must reprime finite scenario state after warmup");
        assertTrue(settle > reprime, "GC settle must happen after scenario reprime");
        assertTrue(settle > 0, "headless runner must settle GC after warmup");
        assertTrue(monitor > settle, "GC monitor must start after the settle boundary");
        assertTrue(begin > settle, "subsystem probes must start after the settle boundary");
        assertTrue(tickWorkload > tickStart, "calibration workload must run inside the measured tick window");
    }

    @Test
    void commandRunnerSettlesGcBeforeVanillaPacedMeasurementStarts() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/ForgeRunController.java"));

        int reprime = source.indexOf("scenarioLoader.reprimeDynamicState(ScenarioSpec.from(config), BenchmarkLevel.level(serverForSetup))");
        int settle = source.indexOf("GcSettler.settleAfterWarmup()");
        int monitor = source.indexOf("new GcMonitor()");
        int begin = source.indexOf("tickProbe.beginMeasurement()");

        assertTrue(reprime > 0, "command runner must reprime finite scenario state after warmup");
        assertTrue(settle > reprime, "GC settle must happen after scenario reprime");
        assertTrue(settle > 0, "command runner must settle GC after warmup");
        assertTrue(monitor > settle, "GC monitor must start after the settle boundary");
        assertTrue(begin > settle, "tick probe must start after the settle boundary");
    }
}
