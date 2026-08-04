package com.virgoagario.swagbench.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoadControllerTest {
    @Test
    void computesDeterministicRampTargets() {
        LoadConfig config = new LoadConfig(true, 4, 4, 10, 2, 123L);

        assertEquals(0, LoadController.targetClientsAtTick(config, 0));
        assertEquals(1, LoadController.targetClientsAtTick(config, 20));
        assertEquals(2, LoadController.targetClientsAtTick(config, 40));
        assertEquals(4, LoadController.targetClientsAtTick(config, 80));
        assertEquals(4, LoadController.targetClientsAtTick(config, 200));
    }

    @Test
    void loadSummaryMarksTargetReachedOnlyWhenAllClientsConnected() {
        LoadSummary summary = LoadController.summaryFor(new LoadConfig(true, 2, 1, 2, 4, 7L), 2, 0);

        assertTrue(summary.reachedTarget());
        assertEquals(2, summary.connectedClients());
    }
}
