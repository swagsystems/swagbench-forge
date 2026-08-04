package com.virgoagario.swagbench.load;

public record LoadConfig(
        boolean enabled,
        int targetClients,
        int rampSeconds,
        int holdSeconds,
        int moveHz,
        long seed) {
    public LoadConfig {
        if (targetClients < 0) {
            throw new IllegalArgumentException("targetClients must be >= 0");
        }
        if (rampSeconds < 0) {
            throw new IllegalArgumentException("rampSeconds must be >= 0");
        }
        if (holdSeconds < 0) {
            throw new IllegalArgumentException("holdSeconds must be >= 0");
        }
        if (moveHz < 1) {
            moveHz = 1;
        }
    }

    public static LoadConfig disabled(long seed) {
        return new LoadConfig(false, 0, 0, 0, 1, seed);
    }
}
