package com.virgoagario.swagbench.network;

import java.util.List;

public record NetworkSummary(
        int connections,
        long bytesIn,
        long bytesOut,
        List<NetworkConnectionSummary> connectionSummaries) {
    public NetworkSummary {
        connectionSummaries = List.copyOf(connectionSummaries == null ? List.of() : connectionSummaries);
    }

    public static NetworkSummary empty() {
        return new NetworkSummary(0, 0L, 0L, List.of());
    }
}
