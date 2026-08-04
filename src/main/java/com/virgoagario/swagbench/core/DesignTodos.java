package com.virgoagario.swagbench.core;

import java.util.List;

public final class DesignTodos {
    public static final List<String> SECTION_12 = List.of(
            "warmup criterion: fixed ticks versus adaptive stabilization with floor/ceiling",
            "measurement loop ownership: uncapped vanilla loop versus minimal harness loop",
            "mixed-v1 exact composition and counts",
            "A/B significance method and whether it lives in-mod or in the future driver",
            "allocation profiling default: enabled for insight or disabled to reduce perturbation",
            "forced-GC aggressiveness/default policy between warmup and measurement");

    private DesignTodos() {
    }
}
