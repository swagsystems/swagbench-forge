package com.virgoagario.swagbench.scenario;

import com.virgoagario.swagbench.core.BenchConfig;

public record ScenarioSpec(String id, long seed) {
    public static final String MIXED_V1 = "mixed-v1";
    public static final String CALIBRATION_V1 = "calibration-v1";
    public static final String POPULATED_V1 = "populated-v1";

    public static ScenarioSpec mixedV1(long seed) {
        return new ScenarioSpec(MIXED_V1, seed);
    }

    public static ScenarioSpec calibrationV1(long seed) {
        return new ScenarioSpec(CALIBRATION_V1, seed);
    }

    public static ScenarioSpec populatedV1(long seed) {
        return new ScenarioSpec(POPULATED_V1, seed);
    }

    public static ScenarioSpec from(BenchConfig config) {
        return from(config.scenario(), config.seed());
    }

    public static ScenarioSpec from(String id, long seed) {
        return switch (id) {
            case MIXED_V1 -> mixedV1(seed);
            case CALIBRATION_V1 -> calibrationV1(seed);
            case POPULATED_V1 -> populatedV1(seed);
            default -> throw new IllegalArgumentException("unsupported scenario: " + id);
        };
    }

    public boolean isMixedV1() {
        return MIXED_V1.equals(id);
    }

    public boolean isCalibrationV1() {
        return CALIBRATION_V1.equals(id);
    }

    public boolean isPopulatedV1() {
        return POPULATED_V1.equals(id);
    }

    public boolean isSupported() {
        return switch (id) {
            case MIXED_V1, CALIBRATION_V1, POPULATED_V1 -> true;
            default -> false;
        };
    }

    public ScenarioSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("scenario id is required");
        }
    }
}
