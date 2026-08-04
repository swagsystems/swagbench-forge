package com.virgoagario.swagbench.load;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class LoadController implements AutoCloseable {
    private static final long CONNECT_TIMEOUT_MILLIS = 0L;
    private static final long LOAD_SETTLE_TIMEOUT_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(45L);
    private final LoadConfig config;
    private final List<ClientAnchor> anchors;
    private final List<LoopbackClient> clients = new ArrayList<>();
    private final AtomicInteger failedClients = new AtomicInteger();
    private final long startNanos = System.nanoTime();
    private volatile int presentPlayers;
    private volatile int peakPresentPlayers;

    public LoadController(LoadConfig config, List<ClientAnchor> anchors) {
        this.config = config;
        this.anchors = List.copyOf(anchors == null ? List.of() : anchors);
    }

    public void tick(MinecraftServer server, ServerLevel level, int tickIndex) {
        int target = targetClientsAtTick(config, tickIndex);
        while (clients.size() < target) {
            int index = clients.size();
            ClientAnchor anchor = anchors.isEmpty()
                    ? new ClientAnchor(0.5, 81.0, 0.5, 0.0F, 0.0F)
                    : anchors.get(index % anchors.size());
            LoopbackClient client = new LoopbackClient("swagbench-" + index, "127.0.0.1", server.getPort(), anchor, config.moveHz());
            clients.add(client);
            Thread connector = new Thread(() -> {
                if (!client.connect(CONNECT_TIMEOUT_MILLIS)) {
                    failedClients.incrementAndGet();
                    client.close();
                }
            }, "swagbench-load-connect-" + index);
            connector.setDaemon(true);
            connector.start();
        }
        Set<String> presentNames = teleportPlayers(server, level);
        for (LoopbackClient client : clients) {
            if (presentNames.contains(client.username())) {
                client.sendMovement(tickIndex);
            }
        }
    }

    public LoadSummary summary() {
        int connectedClients = presentPlayers;
        return summaryFor(config, connectedClients, failedClients.get());
    }

    public int peakPresentPlayers() {
        return peakPresentPlayers;
    }

    public boolean finished(int tickIndex) {
        if (tickIndex < (config.rampSeconds() + config.holdSeconds()) * 20) {
            return false;
        }
        return summary().reachedTarget() || System.nanoTime() - startNanos > LOAD_SETTLE_TIMEOUT_NANOS;
    }

    @Override
    public void close() {
        for (LoopbackClient client : clients) {
            client.close();
        }
        clients.clear();
    }

    public static int targetClientsAtTick(LoadConfig config, int tickIndex) {
        if (!config.enabled() || config.targetClients() == 0) {
            return 0;
        }
        int rampTicks = config.rampSeconds() * 20;
        if (rampTicks <= 0 || tickIndex >= rampTicks) {
            return config.targetClients();
        }
        return Math.min(config.targetClients(), (int) Math.floor(config.targetClients() * (tickIndex / (double) rampTicks)));
    }

    public static LoadSummary summaryFor(LoadConfig config, int connectedClients, int failedClients) {
        return new LoadSummary(
                config.enabled() ? "realistic" : "marginal",
                config.targetClients(),
                connectedClients,
                failedClients,
                config.rampSeconds(),
                config.holdSeconds(),
                config.moveHz(),
                config.seed(),
                connectedClients >= config.targetClients() && failedClients == 0);
    }

    private Set<String> teleportPlayers(MinecraftServer server, ServerLevel level) {
        Set<String> presentNames = new HashSet<>();
        int present = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getGameProfile().getName();
            if (!name.startsWith("swagbench-")) {
                continue;
            }
            present++;
            presentNames.add(name);
            int index = parseClientIndex(name);
            ClientAnchor anchor = anchors.isEmpty()
                    ? new ClientAnchor(0.5, 81.0, 0.5, 0.0F, 0.0F)
                    : anchors.get(Math.floorMod(index, anchors.size()));
            if (player.getLevel() != level || player.distanceToSqr(anchor.x(), anchor.y(), anchor.z()) > 4.0D) {
                player.teleportTo(level, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch());
            }
        }
        presentPlayers = present;
        peakPresentPlayers = Math.max(peakPresentPlayers, present);
        return presentNames;
    }

    private static int parseClientIndex(String name) {
        try {
            return Integer.parseInt(name.substring("swagbench-".length()));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
