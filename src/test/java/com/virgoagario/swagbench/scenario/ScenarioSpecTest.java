package com.virgoagario.swagbench.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.virgoagario.swagbench.core.BenchConfig;
import org.junit.jupiter.api.Test;

class ScenarioSpecTest {
    @Test
    void createsMixedV1FromBenchmarkConfig() {
        ScenarioSpec spec = ScenarioSpec.from(BenchConfig.tiny("mixed-v1", 1234L, 10, 20));

        assertEquals("mixed-v1", spec.id());
        assertEquals(1234L, spec.seed());
    }

    @Test
    void createsCalibrationV1FromBenchmarkConfig() {
        ScenarioSpec spec = ScenarioSpec.from(BenchConfig.tiny("calibration-v1", 5678L, 10, 20));

        assertEquals("calibration-v1", spec.id());
        assertEquals(5678L, spec.seed());
    }

    @Test
    void createsPopulatedV1FromBenchmarkConfig() {
        ScenarioSpec spec = ScenarioSpec.from(BenchConfig.tiny("populated-v1", 99L, 10, 20));

        assertEquals("populated-v1", spec.id());
        assertEquals(99L, spec.seed());
    }

    @Test
    void rejectsUnsupportedScenarioIdsInsteadOfSilentlyFallingBack() {
        BenchConfig config = BenchConfig.tiny("chunkgen-v1", 1234L, 10, 20);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ScenarioSpec.from(config));

        assertEquals("unsupported scenario: chunkgen-v1", error.getMessage());
    }
}
