package com.virgoagario.swagbench.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkByteCounter extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkByteCounter.class);
    private final String id;
    private final LongAdder bytesIn = new LongAdder();
    private final LongAdder bytesOut = new LongAdder();

    public NetworkByteCounter(String id) {
        this.id = id;
        NetworkProbe.register(this);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (NetworkProbe.active() && msg instanceof ByteBuf buffer) {
            bytesIn.add(buffer.readableBytes());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (NetworkProbe.active() && msg instanceof ByteBuf buffer) {
            bytesOut.add(buffer.readableBytes());
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug(
                "SwagBench network counter removed id={} splitter={} decoder={} packet_handler={} handlers={}",
                id,
                ctx.pipeline().get("splitter") != null,
                ctx.pipeline().get("decoder") != null,
                ctx.pipeline().get("packet_handler") != null,
                ctx.pipeline().names().size());
        super.handlerRemoved(ctx);
    }

    NetworkConnectionSummary summary() {
        return new NetworkConnectionSummary(id, bytesIn.sum(), bytesOut.sum());
    }
}
