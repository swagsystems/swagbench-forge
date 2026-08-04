package com.virgoagario.swagbench.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CpuProbeTest {
    @Test
    void computesPerCoreUtilizationFromProcStatDeltas() {
        String before = """
                cpu  0 0 0 0 0 0 0 0 0 0
                cpu0 100 0 0 100 0 0 0 0 0 0
                cpu1 50 0 0 150 0 0 0 0 0 0
                """;
        String after = """
                cpu  0 0 0 0 0 0 0 0 0 0
                cpu0 160 0 0 140 0 0 0 0 0 0
                cpu1 60 0 0 190 0 0 0 0 0 0
                """;

        List<Double> utilization = CpuProbe.perCoreUtilization(before, after);

        assertEquals(2, utilization.size());
        assertEquals(0.60, utilization.get(0), 0.0001);
        assertEquals(0.20, utilization.get(1), 0.0001);
    }

    @Test
    void returnsUnknownUtilizationWhenProcStatCannotBeParsed() {
        assertEquals(List.of(-1.0), CpuProbe.perCoreUtilization("not proc stat", "still not proc stat"));
    }
}
