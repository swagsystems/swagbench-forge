package com.virgoagario.swagbench.core;

import com.virgoagario.swagbench.analysis.BottleneckReport;
import com.virgoagario.swagbench.analysis.LeakReport;
import com.virgoagario.swagbench.load.LoadSummary;
import com.virgoagario.swagbench.network.NetworkSummary;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Report(
        int schemaVersion,
        String mode,
        String runId,
        Instant timestampUtc,
        EnvironmentInfo env,
        BenchConfig config,
        RngInfo rng,
        StatsSet tick,
        StatsSet tickWindows,
        Map<String, StatsSet> subsystems,
        GcSummary gc,
        AllocationSummary alloc,
        ResourceTimeline resources,
        LoadSummary load,
        NetworkSummary network,
        BottleneckReport bottleneck,
        LeakReport leak,
        boolean degraded,
        String degradedReason) {
    public Report(
            int schemaVersion,
            String runId,
            Instant timestampUtc,
            EnvironmentInfo env,
            BenchConfig config,
            RngInfo rng,
            StatsSet tick,
            StatsSet tickWindows,
            Map<String, StatsSet> subsystems,
            GcSummary gc,
            AllocationSummary alloc,
            boolean degraded,
            String degradedReason) {
        this(schemaVersion, "marginal", runId, timestampUtc, env, config, rng, tick, tickWindows, subsystems, gc, alloc,
                null, null, null, null, null, degraded, degradedReason);
    }

    public Report {
        if (runId == null || runId.isBlank()) {
            runId = UUID.randomUUID().toString();
        }
        mode = mode == null || mode.isBlank() ? "marginal" : mode;
        if (timestampUtc == null) {
            timestampUtc = Instant.now();
        }
        if (rng == null) {
            rng = RngInfo.unpinned(config == null ? 0L : config.seed());
        }
        subsystems = Map.copyOf(subsystems == null ? Map.of() : subsystems);
        alloc = alloc == null ? AllocationSummary.disabled() : alloc;
        degradedReason = degradedReason == null ? "" : degradedReason;
    }

    public Report withResources(ResourceTimeline resources, AllocationSummary alloc) {
        return new Report(
                schemaVersion,
                mode,
                runId,
                timestampUtc,
                env,
                config,
                rng,
                tick,
                tickWindows,
                subsystems,
                gc,
                alloc,
                resources,
                load,
                network,
                bottleneck,
                leak,
                degraded,
                degradedReason);
    }

    public Report withMode(String mode) {
        return new Report(
                schemaVersion,
                mode,
                runId,
                timestampUtc,
                env,
                config,
                rng,
                tick,
                tickWindows,
                subsystems,
                gc,
                alloc,
                resources,
                load,
                network,
                bottleneck,
                leak,
                degraded,
                degradedReason);
    }

    public Report withLoad(LoadSummary load) {
        return new Report(
                schemaVersion,
                mode,
                runId,
                timestampUtc,
                env,
                config,
                rng,
                tick,
                tickWindows,
                subsystems,
                gc,
                alloc,
                resources,
                load,
                network,
                bottleneck,
                leak,
                degraded,
                degradedReason);
    }

    public Report withNetwork(NetworkSummary network) {
        return new Report(
                schemaVersion,
                mode,
                runId,
                timestampUtc,
                env,
                config,
                rng,
                tick,
                tickWindows,
                subsystems,
                gc,
                alloc,
                resources,
                load,
                network,
                bottleneck,
                leak,
                degraded,
                degradedReason);
    }

    public Report withAnalyses(BottleneckReport bottleneck, LeakReport leak) {
        return new Report(
                schemaVersion,
                mode,
                runId,
                timestampUtc,
                env,
                config,
                rng,
                tick,
                tickWindows,
                subsystems,
                gc,
                alloc,
                resources,
                load,
                network,
                bottleneck,
                leak,
                degraded,
                degradedReason);
    }

    public Report withDegradation(boolean degraded, String degradedReason) {
        return new Report(
                schemaVersion,
                mode,
                runId,
                timestampUtc,
                env,
                config,
                rng,
                tick,
                tickWindows,
                subsystems,
                gc,
                alloc,
                resources,
                load,
                network,
                bottleneck,
                leak,
                degraded,
                degradedReason);
    }
}
