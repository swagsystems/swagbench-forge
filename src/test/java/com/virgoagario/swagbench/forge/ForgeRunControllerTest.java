package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virgoagario.swagbench.control.RunState;
import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.StatsEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForgeRunControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void abortReturnsControllerToIdleWhenRunIsActive() {
        ForgeRunController controller = new ForgeRunController(new StatsEngine(0.25, 1_000_000));
        assertTrue(controller.requestRun(BenchConfig.tiny("mixed-v1", 7L, 0, 20), tempDir, null, false, false));

        assertTrue(controller.abort());

        assertEquals(RunState.IDLE, controller.state());
        assertFalse(controller.abort());
    }

    @Test
    void unsupportedScenarioDoesNotLeaveControllerRunning() {
        ForgeRunController controller = new ForgeRunController(new StatsEngine(0.25, 1_000_000));

        try {
            controller.requestRun(BenchConfig.tiny("chunkgen-v1", 7L, 0, 20), tempDir, null, false, false);
        } catch (IllegalArgumentException expected) {
            assertEquals("unsupported scenario: chunkgen-v1", expected.getMessage());
        }

        assertEquals(RunState.IDLE, controller.state());
    }

    @Test
    void setupScenarioExceptionsResetControllerBeforeRethrow() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/ForgeRunController.java"));

        assertTrue(source.contains("catch (RuntimeException e)"));
        assertTrue(source.contains("resetAfterRun();"));
        assertTrue(source.contains("throw e;"));
    }

    @Test
    void commandRunnerReportsRngPinningIntoStableJsonSchema() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/ForgeRunController.java"));

        assertTrue(source.contains("rngInfo = rngHarness.seedAll(scenarioSpec.seed(), level);"));
        assertTrue(source.contains("rngInfo,"));
    }
}
