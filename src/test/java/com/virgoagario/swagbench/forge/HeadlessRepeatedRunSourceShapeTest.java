package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HeadlessRepeatedRunSourceShapeTest {
    @Test
    void headlessEntryUsesSameJvmRepeatedRunner() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/HeadlessEntry.java"));

        assertTrue(source.contains("parseRequest(runProperty)"));
        assertTrue(source.contains("runRepeated(server, parseRequest(runProperty), outputDir)"));
    }

    @Test
    void headlessEntryLogsOuterSetupFailuresBeforeExitCodeFallback() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/HeadlessEntry.java"));

        assertTrue(source.contains("private static final Logger LOGGER = LogUtils.getLogger()"));
        assertTrue(source.contains("LOGGER.error(\"SwagBench headless entry failed\", e)"));
    }

    @Test
    void uncappedRunnerAggregatesRepeatedRunExitCodes() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/UncappedHeadlessRunner.java"));

        assertTrue(source.contains("int runRepeated("));
        assertTrue(source.contains("for (int i = 0; i < request.runs(); i++)"));
        assertTrue(source.contains("return ExitCodes.SETUP_FAILURE"));
        assertTrue(source.contains("return sawDegraded ? ExitCodes.DEGRADED : ExitCodes.OK"));
    }

    @Test
    void setupFailuresAreLoggedBeforeReturningSetupFailure() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/UncappedHeadlessRunner.java"));

        assertTrue(source.contains("private static final Logger LOGGER = LogUtils.getLogger()"));
        assertTrue(source.contains("LOGGER.error(\"SwagBench headless run failed\", e)"));
    }

    @Test
    void headlessRunnerDisablesBackgroundWorldActivityBeforeMeasuring() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/UncappedHeadlessRunner.java"));

        assertTrue(source.contains("disableBackgroundWorldActivity(server)"));
        assertTrue(source.contains("server.getAllLevels()"));
        assertTrue(source.contains("GameRules.RULE_DOMOBSPAWNING"));
        assertTrue(source.contains("GameRules.RULE_DO_PATROL_SPAWNING"));
        assertTrue(source.contains("GameRules.RULE_DO_TRADER_SPAWNING"));
        assertTrue(source.contains("GameRules.RULE_DAYLIGHT"));
        assertTrue(source.contains("GameRules.RULE_WEATHER_CYCLE"));
        assertTrue(source.contains("GameRules.RULE_RANDOMTICKING"));
    }

    @Test
    void headlessRunnerReportsRngPinningIntoStableJsonSchema() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/UncappedHeadlessRunner.java"));

        assertTrue(source.contains("RngInfo rngInfo = rngHarness.seedAll(config.seed(), level);"));
        assertTrue(source.contains("rngInfo,"));
    }

    @Test
    void smokeScriptCanRequestAndValidateMultipleSameJvmReports() throws IOException {
        String source = Files.readString(Path.of("scripts/headless-smoke.sh"));

        assertTrue(source.contains("RUNS=\"${SWAGBENCH_SMOKE_RUNS:-1}\""));
        assertTrue(source.contains("runs=${RUNS}"));
        assertTrue(source.contains("expected_reports"));
        assertTrue(source.contains("len(reports) == expected_reports"));
    }

    @Test
    void repeatabilityPreflightAvoidsGawkBuiltinVariableNames() throws IOException {
        String source = Files.readString(Path.of("scripts/headless-repeatability.sh"));

        assertTrue(source.contains("-v load_avg=\"$load1\""));
        assertTrue(source.contains("load_avg > cores"));
    }
}
