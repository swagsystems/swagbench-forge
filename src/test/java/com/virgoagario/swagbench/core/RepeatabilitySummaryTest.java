package com.virgoagario.swagbench.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RepeatabilitySummaryTest {
    @Test
    void acceptsWideSpreadWhenCrossRunMadAndPerRunMadStayUnderThreshold() {
        RepeatabilitySummary summary = RepeatabilitySummary.fromStats(
                List.of(
                        new StatsBlock(1, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000.0, 0.0, 5_000.0),
                        new StatsBlock(1, 1_004_000, 1_004_000, 1_004_000, 1_004_000, 1_004_000, 1_004_000, 1_004_000, 1_004_000.0, 0.0, 5_000.0),
                        new StatsBlock(1, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000.0, 0.0, 5_000.0)),
                0.01);

        assertTrue(summary.passes());
    }

    @Test
    void rejectsHighCrossRunMedianMad() {
        RepeatabilitySummary summary = RepeatabilitySummary.fromStats(
                List.of(
                        new StatsBlock(1, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000.0, 0.0, 5_000.0),
                        new StatsBlock(1, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000, 1_020_000.0, 0.0, 5_000.0),
                        new StatsBlock(1, 1_040_000, 1_040_000, 1_040_000, 1_040_000, 1_040_000, 1_040_000, 1_040_000, 1_040_000.0, 0.0, 5_000.0)),
                0.01);

        assertFalse(summary.passes());
    }
}
