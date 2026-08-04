package com.virgoagario.swagbench.forge;

import com.mojang.logging.LogUtils;
import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.ExitCodes;
import com.virgoagario.swagbench.core.StatsEngine;
import com.virgoagario.swagbench.load.LoadConfig;
import com.virgoagario.swagbench.resource.ResourceSamplerConfig;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class HeadlessEntry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String RUN_PROPERTY = "swagbench.run";
    public static final String OUTPUT_PROPERTY = "swagbench.output";

    private HeadlessEntry() {
    }

    public static boolean maybeRun(MinecraftServer server) {
        String runProperty = System.getProperty(RUN_PROPERTY);
        if (runProperty == null || runProperty.isBlank()) {
            return false;
        }
        Path outputDir = Path.of(System.getProperty(OUTPUT_PROPERTY, "swagbench-reports"));
        int exitCode;
        try {
            exitCode = new UncappedHeadlessRunner(new StatsEngine(0.25, 1_000_000))
                    .runRepeated(server, parseRequest(runProperty), outputDir);
        } catch (RuntimeException e) {
            LOGGER.error("SwagBench headless entry failed", e);
            exitCode = ExitCodes.SETUP_FAILURE;
        }
        server.halt(false);
        if (!Boolean.getBoolean("swagbench.noExit")) {
            exitAsync(exitCode, System::exit);
        }
        return true;
    }

    static Thread exitAsync(int exitCode, IntConsumer exitAction) {
        Thread thread = new Thread(() -> exitAction.accept(exitCode), "swagbench-headless-exit");
        thread.setDaemon(false);
        thread.start();
        return thread;
    }

    public static BenchConfig parseConfig(String runProperty) {
        return parseRequest(runProperty).config();
    }

    static HeadlessRunRequest parseRequest(String runProperty) {
        // Stable, intentionally small contract for Approach C: absent fields keep defaults.
        // Format: scenario=mixed-v1,seed=0,warmup=2000,measure=2000,runs=1
        String scenario = "mixed-v1";
        long seed = 0L;
        int warmup = 2_000;
        int measure = 2_000;
        int runs = 1;
        String mode = "marginal";
        boolean scenarioSet = false;
        int clients = 0;
        int rampSeconds = 0;
        int holdSeconds = 0;
        int moveHz = 2;
        boolean resourceEnabled = false;
        long resourceIntervalMillis = ResourceSamplerConfig.DEFAULT_INTERVAL_MILLIS;
        boolean resourceFull = false;
        for (String part : runProperty.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0].trim()) {
                case "scenario" -> {
                    scenario = kv[1].trim();
                    scenarioSet = true;
                }
                case "seed" -> seed = Long.parseLong(kv[1].trim());
                case "warmup" -> warmup = Integer.parseInt(kv[1].trim());
                case "measure" -> measure = Integer.parseInt(kv[1].trim());
                case "runs" -> runs = Integer.parseInt(kv[1].trim());
                case "mode" -> mode = kv[1].trim();
                case "clients" -> clients = Integer.parseInt(kv[1].trim());
                case "rampSeconds" -> rampSeconds = Integer.parseInt(kv[1].trim());
                case "holdSeconds" -> holdSeconds = Integer.parseInt(kv[1].trim());
                case "moveHz" -> moveHz = Integer.parseInt(kv[1].trim());
                case "resource" -> resourceEnabled = parseBooleanFlag(kv[1]);
                case "resourceIntervalMillis" -> resourceIntervalMillis = Long.parseLong(kv[1].trim());
                case "resourceFull" -> resourceFull = parseBooleanFlag(kv[1]);
                default -> {
                }
            }
        }
        boolean realistic = "realistic".equals(mode);
        resourceEnabled |= realistic;
        if (realistic && !scenarioSet) {
            scenario = "populated-v1";
        }
        return new HeadlessRunRequest(
                new BenchConfig(scenario, seed, warmup, measure),
                runs,
                new ResourceSamplerConfig(resourceEnabled, resourceIntervalMillis, resourceFull),
                mode,
                new LoadConfig(realistic, clients, rampSeconds, holdSeconds, moveHz, seed));
    }

    private static boolean parseBooleanFlag(String value) {
        String normalized = value.trim();
        return normalized.equalsIgnoreCase("true")
                || normalized.equalsIgnoreCase("on")
                || normalized.equals("1")
                || normalized.equalsIgnoreCase("yes");
    }

}
