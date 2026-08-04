package com.virgoagario.swagbench.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DiskProbeTest {
    @Test
    void computesReadAndWriteByteDeltasFromProcSelfIo() {
        DiskProbe.IoSnapshot before = DiskProbe.IoSnapshot.parse("""
                rchar: 10
                wchar: 20
                read_bytes: 1000
                write_bytes: 2000
                """);
        DiskProbe.IoSnapshot after = DiskProbe.IoSnapshot.parse("""
                rchar: 40
                wchar: 80
                read_bytes: 1500
                write_bytes: 2600
                """);

        DiskProbe.IoDelta delta = DiskProbe.IoDelta.between(before, after);

        assertEquals(500L, delta.readBytes());
        assertEquals(600L, delta.writeBytes());
    }

    @Test
    void unknownIoSnapshotProducesUnknownDeltas() {
        DiskProbe.IoDelta delta = DiskProbe.IoDelta.between(
                DiskProbe.IoSnapshot.unknown(),
                DiskProbe.IoSnapshot.parse("read_bytes: 1500\nwrite_bytes: 2600\n"));

        assertEquals(-1L, delta.readBytes());
        assertEquals(-1L, delta.writeBytes());
    }
}
