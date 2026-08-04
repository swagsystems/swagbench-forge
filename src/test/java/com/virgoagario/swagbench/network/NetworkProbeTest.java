package com.virgoagario.swagbench.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class NetworkProbeTest {
    @Test
    void byteCounterAggregatesInboundAndOutboundBytesWhileProbeIsActive() {
        NetworkProbe.begin();
        EmbeddedChannel channel = new EmbeddedChannel(new NetworkByteCounter("client-1"));

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {1, 2, 3}));
        channel.writeOutbound(Unpooled.wrappedBuffer(new byte[] {4, 5, 6, 7}));
        NetworkSummary summary = NetworkProbe.snapshot();
        NetworkProbe.end();

        assertEquals(1, summary.connections());
        assertEquals(3L, summary.bytesIn());
        assertEquals(4L, summary.bytesOut());
        assertEquals("client-1", summary.connectionSummaries().get(0).id());
    }
}
