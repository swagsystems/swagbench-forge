package com.virgoagario.swagbench.core;

public record GcSummary(int events, double totalPauseMs, int contaminatedTicks) {
}
