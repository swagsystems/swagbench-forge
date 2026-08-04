package com.virgoagario.swagbench.network;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkProbe.class);
    private static final String HANDLER_NAME = "swagbench_network_probe";
    private static final Queue<NetworkByteCounter> COUNTERS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private NetworkProbe() {
    }

    public static void begin() {
        COUNTERS.clear();
        ACTIVE.set(true);
    }

    public static void end() {
        ACTIVE.set(false);
    }

    public static boolean active() {
        return ACTIVE.get();
    }

    public static void install(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (pipeline.get(HANDLER_NAME) != null || pipeline.get("splitter") == null) {
            return;
        }
        LOGGER.debug(
                "SwagBench installing network probe splitter={} decoder={} packet_handler={} handlers={}",
                pipeline.get("splitter") != null,
                pipeline.get("decoder") != null,
                pipeline.get("packet_handler") != null,
                pipeline.names().size());
        ChannelHandler handler = new NetworkByteCounter("connection-" + UUID.randomUUID());
        pipeline.addBefore("splitter", HANDLER_NAME, handler);
        LOGGER.debug(
                "SwagBench installed network probe splitter={} decoder={} packet_handler={} handlers={}",
                pipeline.get("splitter") != null,
                pipeline.get("decoder") != null,
                pipeline.get("packet_handler") != null,
                pipeline.names().size());
    }

    static void register(NetworkByteCounter counter) {
        COUNTERS.add(counter);
    }

    public static NetworkSummary snapshot() {
        List<NetworkConnectionSummary> connections = COUNTERS.stream()
                .map(NetworkByteCounter::summary)
                .sorted(Comparator.comparing(NetworkConnectionSummary::id))
                .toList();
        long bytesIn = connections.stream().mapToLong(NetworkConnectionSummary::bytesIn).sum();
        long bytesOut = connections.stream().mapToLong(NetworkConnectionSummary::bytesOut).sum();
        return new NetworkSummary(connections.size(), bytesIn, bytesOut, connections);
    }
}
