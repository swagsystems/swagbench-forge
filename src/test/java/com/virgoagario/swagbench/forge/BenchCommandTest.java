package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.virgoagario.swagbench.core.StatsEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

class BenchCommandTest {
    @Test
    void registersRunStatusAbortAndConfigCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        BenchCommand.register(dispatcher, new ForgeRunController(new StatsEngine(0.25, 1_000_000)), Path.of("reports"));

        var swagbench = dispatcher.getRoot().getChild("swagbench");
        assertNotNull(swagbench.getChild("run"));
        assertNotNull(swagbench.getChild("status"));
        assertNotNull(swagbench.getChild("abort"));
        assertNotNull(swagbench.getChild("config"));
    }

    @Test
    void runCommandAcceptsExplicitScenarioSeedWarmupAndMeasureArguments() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        BenchCommand.register(dispatcher, new ForgeRunController(new StatsEngine(0.25, 1_000_000)), Path.of("reports"));

        var run = dispatcher.getRoot().getChild("swagbench").getChild("run");
        var scenario = run.getChild("scenario");
        var seed = scenario.getChild("seed");
        var warmup = seed.getChild("warmup");
        var measure = warmup.getChild("measure");
        assertNotNull(measure);
    }

    @Test
    void defaultCommandConfigUsesVerifiedWarmupAndMeasurementWindow() {
        var config = BenchCommand.defaultConfig();

        assertEquals(2_000, config.warmupTicks());
        assertEquals(2_000, config.measureTicks());
    }

    @Test
    void runCommandReportsSetupFailuresAsChatErrors() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/BenchCommand.java"));

        assertTrue(source.contains("catch (RuntimeException e)"));
        assertTrue(source.contains("source.sendFailure("));
        assertTrue(source.contains("SwagBench run failed: "));
        assertTrue(source.contains("return 0;"));
    }
}
