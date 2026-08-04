package com.virgoagario.swagbench.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiskProbe {
    private IoSnapshot previous = IoSnapshot.unknown();

    public IoDelta sample() {
        IoSnapshot current;
        try {
            current = IoSnapshot.parse(Files.readString(Path.of("/proc/self/io")));
        } catch (IOException e) {
            current = IoSnapshot.unknown();
        }
        IoDelta delta = IoDelta.between(previous, current);
        previous = current;
        return delta;
    }

    public record IoSnapshot(long readBytes, long writeBytes) {
        public static IoSnapshot unknown() {
            return new IoSnapshot(-1L, -1L);
        }

        public static IoSnapshot parse(String text) {
            long readBytes = -1L;
            long writeBytes = -1L;
            if (text == null) {
                return unknown();
            }
            for (String line : text.split("\\R")) {
                String[] parts = line.trim().split(":\\s*", 2);
                if (parts.length != 2) {
                    continue;
                }
                try {
                    if (parts[0].equals("read_bytes")) {
                        readBytes = Long.parseLong(parts[1].trim());
                    } else if (parts[0].equals("write_bytes")) {
                        writeBytes = Long.parseLong(parts[1].trim());
                    }
                } catch (NumberFormatException ignored) {
                    return unknown();
                }
            }
            if (readBytes < 0L || writeBytes < 0L) {
                return unknown();
            }
            return new IoSnapshot(readBytes, writeBytes);
        }
    }

    public record IoDelta(long readBytes, long writeBytes) {
        public static IoDelta unknown() {
            return new IoDelta(-1L, -1L);
        }

        public static IoDelta between(IoSnapshot before, IoSnapshot after) {
            if (before == null || after == null
                    || before.readBytes() < 0L || before.writeBytes() < 0L
                    || after.readBytes() < 0L || after.writeBytes() < 0L) {
                return unknown();
            }
            return new IoDelta(
                    Math.max(0L, after.readBytes() - before.readBytes()),
                    Math.max(0L, after.writeBytes() - before.writeBytes()));
        }
    }
}
