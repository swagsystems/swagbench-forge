package com.virgoagario.swagbench.core;

public record RngInfo(long seed, String dimension, boolean fullyPinned) {
    public RngInfo {
        dimension = dimension == null || dimension.isBlank() ? "unknown" : dimension;
    }

    public static RngInfo unpinned(long seed) {
        return new RngInfo(seed, "unknown", false);
    }
}
