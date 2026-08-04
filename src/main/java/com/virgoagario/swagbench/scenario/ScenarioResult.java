package com.virgoagario.swagbench.scenario;

import com.virgoagario.swagbench.core.GcEvent;
import com.virgoagario.swagbench.core.TickSample;
import java.util.List;

public record ScenarioResult(List<TickSample> samples, List<GcEvent> gcEvents) {
    public ScenarioResult {
        samples = List.copyOf(samples == null ? List.of() : samples);
        gcEvents = List.copyOf(gcEvents == null ? List.of() : gcEvents);
    }
}
