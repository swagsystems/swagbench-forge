package com.virgoagario.swagbench.load;

public record LoadSummary(
        String mode,
        int targetClients,
        int connectedClients,
        int failedClients,
        int rampSeconds,
        int holdSeconds,
        int moveHz,
        long seed,
        boolean reachedTarget) {
}
