package com.virgoagario.swagbench.scenario;

import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.GcEvent;
import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.core.TickSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SyntheticScenarioRunner implements ScenarioRunner {
    private final boolean contaminateGc;

    private SyntheticScenarioRunner(boolean contaminateGc) {
        this.contaminateGc = contaminateGc;
    }

    public static SyntheticScenarioRunner mixedV1() {
        return new SyntheticScenarioRunner(false);
    }

    public static SyntheticScenarioRunner gcContaminated() {
        return new SyntheticScenarioRunner(true);
    }

    @Override
    public ScenarioResult run(BenchConfig config) {
        if (!"mixed-v1".equals(config.scenario())) {
            throw new IllegalArgumentException("unsupported scenario: " + config.scenario());
        }

        List<TickSample> samples = new ArrayList<>();
        long cursor = 0L;
        for (int i = 0; i < config.measureTicks(); i++) {
            long duration = durationFor(config.seed(), i);
            samples.add(TickSample.of(i, cursor, cursor + duration, bucketsFor(duration, config.seed(), i)));
            cursor += duration;
        }

        List<GcEvent> gcEvents = List.of();
        if (contaminateGc && !samples.isEmpty()) {
            TickSample first = samples.get(0);
            TickSample last = samples.get(samples.size() - 1);
            gcEvents = List.of(new GcEvent("synthetic-gc", first.startNanos(), last.endNanos()));
        }
        return new ScenarioResult(samples, gcEvents);
    }

    private static long durationFor(long seed, int tick) {
        long phase = Math.floorMod(seed + tick * 31L, 5L);
        return 1_000_000L + (phase - 2L) * 100L;
    }

    private static Map<SubsystemBucket, Long> bucketsFor(long duration, long seed, int tick) {
        long wobble = Math.floorMod(seed + tick * 17L, 7L);
        long entities = 300_000L + wobble * 10L;
        long blockEntities = 175_000L + wobble * 5L;
        long chunk = 125_000L;
        long scheduled = 75_000L;
        long named = entities + blockEntities + chunk + scheduled;
        if (named > duration) {
            entities = Math.max(0L, entities - (named - duration));
        }
        return Map.of(
                SubsystemBucket.ENTITIES, entities,
                SubsystemBucket.BLOCK_ENTITIES, blockEntities,
                SubsystemBucket.CHUNK, chunk,
                SubsystemBucket.SCHEDULED, scheduled);
    }
}
