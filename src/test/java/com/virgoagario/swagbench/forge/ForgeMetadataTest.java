package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class ForgeMetadataTest {
    @Test
    void modsTomlAdvertisesMixinConfigForUserdevAndPackagedLoads() throws IOException {
        String modsToml = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));

        assertTrue(modsToml.contains("[[mixins]]"), "mods.toml must declare Forge mixin configs");
        assertTrue(modsToml.contains("config=\"${mod_id}.mixins.json\""),
                "mods.toml must load SwagBench's mixin config");
    }

    @Test
    void forgeGradleUserdevPassesMixinConfigToRunTargets() throws IOException {
        String buildGradle = Files.readString(Path.of("build.gradle"));

        assertTrue(buildGradle.contains("--mixin.config=${mod_id}.mixins.json"),
                "ForgeGradle run targets must pass the mixin config explicitly in userdev");
    }

    @Test
    void productionJarBuildGeneratesAndDeclaresMixinRefmap() throws IOException {
        String buildGradle = Files.readString(Path.of("build.gradle"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));

        assertTrue(buildGradle.contains("apply plugin: 'org.spongepowered.mixin'"),
                "production builds must enable MixinGradle so refmap data is generated");
        assertTrue(buildGradle.contains("add sourceSets.main, 'swagbench.refmap.json'"),
                "MixinGradle must generate the refmap named by the mixin config");
        assertTrue(buildGradle.contains("config 'swagbench.mixins.json'"),
                "MixinGradle must attach the mixin config to obfuscated jars");
        assertTrue(mixinConfig.contains("\"refmap\": \"swagbench.refmap.json\""),
                "mixin config must name the packaged refmap used by production servers");
    }

    @Test
    void forgeGradleRunTargetsUseStableBenchmarkJvmArgs() throws IOException {
        String buildGradle = Files.readString(Path.of("build.gradle"));

        assertTrue(buildGradle.contains("swagbenchRunXms"),
                "benchmark run initial heap should be configurable for contention-free repeatability probes");
        assertTrue(buildGradle.contains("swagbenchRunXmx"),
                "benchmark run max heap should be configurable for contention-free repeatability probes");
        assertTrue(buildGradle.contains("swagbenchRunXms') ?: '2G'"),
                "benchmark run initial heap should default below the live Crafty heap");
        assertTrue(buildGradle.contains("swagbenchRunXmx') ?: '2G'"),
                "benchmark run max heap should default below the live Crafty heap");
        assertTrue(buildGradle.contains("-XX:+AlwaysPreTouch"), "benchmark runs should pre-touch heap pages");
        assertTrue(buildGradle.contains("-XX:+UseG1GC"), "benchmark runs should use an explicit GC");
        assertTrue(buildGradle.contains("-XX:ActiveProcessorCount=4"), "benchmark runs should pin visible CPU count");
        assertTrue(buildGradle.contains("-XX:ParallelGCThreads=3"), "benchmark runs should pin parallel GC threads");
        assertTrue(buildGradle.contains("-XX:ConcGCThreads=1"), "benchmark runs should pin concurrent GC threads");
        assertTrue(buildGradle.contains("forge.logging.console.level', 'warn'"), "benchmark runs should avoid debug logging");
        assertTrue(buildGradle.contains("forge.disableVersionCheck', 'true'"), "benchmark runs should disable network version checks");
    }

    @Test
    void benchmarkDimensionIsBundledAsVoidFlatWorld() throws IOException {
        String dimension = Files.readString(Path.of("src/main/resources/data/swagbench/dimension/benchmark.json"));
        String dimensionType = Files.readString(Path.of("src/main/resources/data/swagbench/dimension_type/benchmark.json"));

        assertTrue(dimension.contains("\"type\": \"swagbench:benchmark\""));
        assertTrue(dimension.contains("\"type\": \"minecraft:flat\""));
        assertTrue(dimension.contains("\"layers\": []"), "benchmark dimension should not generate terrain blocks");
        assertTrue(dimensionType.contains("\"fixed_time\": 6000"));
        assertTrue(dimensionType.contains("\"has_raids\": false"));
    }

    @Test
    void mixedV1StructureIsBundledAsParseableNbt() throws IOException {
        var tag = NbtIo.readCompressed(Path.of(
                "src/main/resources/data/swagbench/structures/mixed_v1.nbt").toFile());

        assertTrue(tag.getList("size", 3).size() == 3, "structure must declare size");
        assertFalse(tag.getList("palette", 10).isEmpty(), "structure must declare a block palette");
        assertFalse(tag.getList("blocks", 10).isEmpty(), "structure must contain placed blocks");
    }

    @Test
    void forgeRunnersUseDedicatedBenchmarkDimension() throws IOException {
        String headlessRunner = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/UncappedHeadlessRunner.java"));
        String commandRunner = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/forge/ForgeRunController.java"));

        assertTrue(headlessRunner.contains("BenchmarkLevel.level(server)"));
        assertTrue(commandRunner.contains("BenchmarkLevel.level(server)"));
        assertFalse(headlessRunner.contains("server.overworld()"));
        assertFalse(commandRunner.contains("server.overworld()"));
    }

    @Test
    void ciSmokeScriptRunsHeadlessAndValidatesReportContract() throws IOException {
        String smoke = Files.readString(Path.of("scripts/headless-smoke.sh"));

        assertTrue(smoke.contains("./gradlew runServer --no-daemon"));
        assertTrue(smoke.contains("SWAGBENCH_SMOKE_XMS:-2G"));
        assertTrue(smoke.contains("SWAGBENCH_SMOKE_XMX:-2G"));
        assertTrue(smoke.contains("-PswagbenchRunXms=${XMS}"));
        assertTrue(smoke.contains("-PswagbenchRunXmx=${XMX}"));
        assertTrue(smoke.contains("-Dswagbench.run=scenario=mixed-v1"));
        assertTrue(smoke.contains("schemaVersion"));
        assertTrue(smoke.contains("measureTicks"));
        assertTrue(smoke.contains("swagbench-*.json"));
        assertTrue(smoke.contains("exit code 0 or 3"));
        assertTrue(smoke.contains("rm -rf \"$OUT\" run"), "smoke runs must start from a fresh server directory");
    }

    @Test
    void repeatabilityScriptRunsHeadlessAndFailsAboveMadThreshold() throws IOException {
        String repeatability = Files.readString(Path.of("scripts/headless-repeatability.sh"));

        assertTrue(repeatability.contains("SWAGBENCH_REPEAT_SCENARIO"));
        assertTrue(repeatability.contains("SWAGBENCH_REPEAT_THRESHOLD"));
        assertTrue(repeatability.contains("SWAGBENCH_REPEAT_XMS:-2G"));
        assertTrue(repeatability.contains("SWAGBENCH_REPEAT_XMX:-2G"));
        assertTrue(repeatability.contains("SWAGBENCH_REPEAT_SKIP_PREFLIGHT:-0"));
        assertTrue(repeatability.contains("SWAGBENCH_REPEAT_RESOURCE:-0"));
        assertTrue(repeatability.contains("SWAGBENCH_REPEAT_RESOURCE_INTERVAL_MILLIS:-250"));
        assertTrue(repeatability.contains("memory.swap.current"));
        assertTrue(repeatability.contains("/proc/loadavg"));
        assertTrue(repeatability.contains("environment preflight failed"));
        assertTrue(repeatability.contains("-PswagbenchRunXms=${XMS}"));
        assertTrue(repeatability.contains("-PswagbenchRunXmx=${XMX}"));
        assertTrue(repeatability.contains("runs=${RUNS}"));
        assertTrue(repeatability.contains("resource=true,resourceIntervalMillis=${RESOURCE_INTERVAL_MILLIS}"));
        assertTrue(repeatability.contains("\"tickWindows\""));
        assertTrue(repeatability.contains("\"medianNanos\""));
        assertTrue(repeatability.contains("\"madNanos\""));
        assertTrue(repeatability.contains("median_spread_ratio"));
        assertTrue(repeatability.contains("cross_run_mad_ratio"));
        assertTrue(repeatability.contains("sys.exit(1)"));
    }
}
