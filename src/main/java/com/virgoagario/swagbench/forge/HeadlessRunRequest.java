package com.virgoagario.swagbench.forge;

import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.load.LoadConfig;
import com.virgoagario.swagbench.resource.ResourceSamplerConfig;

record HeadlessRunRequest(
        BenchConfig config,
        int runs,
        ResourceSamplerConfig resourceSampler,
        String mode,
        LoadConfig load) {
    HeadlessRunRequest(BenchConfig config, int runs) {
        this(config, runs, ResourceSamplerConfig.disabled(), "marginal", LoadConfig.disabled(config.seed()));
    }

    HeadlessRunRequest {
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be >= 1");
        }
        resourceSampler = resourceSampler == null ? ResourceSamplerConfig.disabled() : resourceSampler;
        mode = mode == null || mode.isBlank() ? "marginal" : mode;
        load = load == null ? LoadConfig.disabled(config.seed()) : load;
    }
}
