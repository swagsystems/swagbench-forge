package com.virgoagario.swagbench.scenario;

import com.virgoagario.swagbench.core.BenchConfig;

@FunctionalInterface
public interface ScenarioRunner {
    ScenarioResult run(BenchConfig config) throws Exception;
}
