package com.virgoagario.swagbench.load;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoopbackClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoopbackClient.class);
    public static final int PROTOCOL_VERSION = 758;
    private final String username;
    private final String host;
    private final int port;
    private final ClientAnchor anchor;
    private final int moveHz;
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private volatile Channel channel;
    private volatile boolean loginComplete;
    private volatile boolean connected;
    private volatile int compressionThreshold = -1;

    public LoopbackClient(String username, String host, int port, ClientAnchor anchor, int moveHz) {
        this.username = username;
        this.host = host;
        this.port = port;
        this.anchor = anchor;
        this.moveHz = Math.max(1, moveHz);
    }

    public boolean connect(long timeoutMillis) {
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new ProtocolHandler());
                        }
                    });
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            channel.writeAndFlush(Unpooled.wrappedBuffer(handshakeFrame(host, port)));
            channel.writeAndFlush(Unpooled.wrappedBuffer(loginStartFrame(username)));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (!connected && channel.isOpen() && (timeoutMillis <= 0L || System.nanoTime() < deadline)) {
                Thread.sleep(25L);
            }
            return connected;
        } catch (Exception e) {
            return false;
        }
    }

    public void sendMovement(int tickIndex) {
        Channel active = channel;
        if (active == null || !active.isOpen() || !connected || tickIndex % Math.max(1, 20 / moveHz) != 0) {
            return;
        }
        double offset = ((tickIndex / Math.max(1, 20 / moveHz)) % 8) * 0.05;
        active.writeAndFlush(Unpooled.wrappedBuffer(positionLookFrame(
                anchor.x() + offset,
                anchor.y(),
                anchor.z() + offset,
                anchor.yaw(),
                anchor.pitch(),
                true,
                compressionThreshold >= 0,
                compressionThreshold)));
    }

    public boolean connected() {
        return connected && channel != null && channel.isOpen();
    }

    public String username() {
        return username;
    }

    @Override
    public void close() {
        Channel active = channel;
        if (active != null) {
            active.close();
        }
        group.shutdownGracefully(0L, 1L, TimeUnit.SECONDS);
    }

    public static byte[] handshakeFrame(String host, int port) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x00);
        MinecraftVarInt.write(payload, PROTOCOL_VERSION);
        writeString(payload, host + "\0FML3\0");
        payload.write((port >>> 8) & 0xff);
        payload.write(port & 0xff);
        MinecraftVarInt.write(payload, 2);
        return frame(payload.toByteArray(), false, -1);
    }

    public static byte[] loginStartFrame(String username) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x00);
        writeString(payload, username);
        return frame(payload.toByteArray(), false, -1);
    }

    public static byte[] keepAliveResponseFrame(long id, boolean compressed, int threshold) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x0f);
        writeLong(payload, id);
        return frame(payload.toByteArray(), compressed, threshold);
    }

    public static byte[] teleportAcceptFrame(int teleportId, boolean compressed, int threshold) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x00);
        MinecraftVarInt.write(payload, teleportId);
        return frame(payload.toByteArray(), compressed, threshold);
    }

    static byte[] positionLookFrame(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            boolean onGround,
            boolean compressed,
            int threshold) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x12);
        writeDouble(payload, x);
        writeDouble(payload, y);
        writeDouble(payload, z);
        writeFloat(payload, yaw);
        writeFloat(payload, pitch);
        payload.write(onGround ? 1 : 0);
        return frame(payload.toByteArray(), compressed, threshold);
    }

    private static byte[] loginPluginResponseFrame(int transactionId, boolean compressed, int threshold) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x02);
        MinecraftVarInt.write(payload, transactionId);
        payload.write(0);
        return frame(payload.toByteArray(), compressed, threshold);
    }

    static byte[] forgeHandshakeAckFrame(int transactionId, boolean compressed, int threshold) {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        MinecraftVarInt.write(inner, 99);
        return forgeLoginWrapperResponseFrame(transactionId, inner.toByteArray(), compressed, threshold);
    }

    static byte[] forgeModListReplyFrame(int transactionId, boolean compressed, int threshold) {
        return forgeModListReplyFrame(transactionId, defaultForgeChannels(), compressed, threshold);
    }

    private static byte[] forgeModListReplyFrame(
            int transactionId,
            Map<String, String> channels,
            boolean compressed,
            int threshold) {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        MinecraftVarInt.write(inner, 2);
        MinecraftVarInt.write(inner, 3);
        writeString(inner, "minecraft");
        writeString(inner, "forge");
        writeString(inner, "swagbench");
        MinecraftVarInt.write(inner, channels.size());
        channels.forEach((channel, version) -> {
            writeString(inner, channel);
            writeString(inner, version);
        });
        MinecraftVarInt.write(inner, 0);
        return forgeLoginWrapperResponseFrame(transactionId, inner.toByteArray(), compressed, threshold);
    }

    private static byte[] forgeLoginWrapperResponseFrame(
            int transactionId,
            byte[] inner,
            boolean compressed,
            int threshold) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x02);
        MinecraftVarInt.write(payload, transactionId);
        payload.write(1);
        writeString(payload, "fml:handshake");
        MinecraftVarInt.write(payload, inner.length);
        payload.writeBytes(inner);
        return frame(payload.toByteArray(), compressed, threshold);
    }

    private static byte[] clientInformationFrame(boolean compressed, int threshold) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MinecraftVarInt.write(payload, 0x05);
        writeString(payload, "en_us");
        payload.write(10);
        MinecraftVarInt.write(payload, 0);
        payload.write(1);
        payload.write(0x7f);
        MinecraftVarInt.write(payload, 1);
        payload.write(0);
        payload.write(1);
        return frame(payload.toByteArray(), compressed, threshold);
    }

    private static byte[] frame(byte[] payload, boolean compressed, int threshold) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (compressed) {
            if (payload.length >= threshold) {
                MinecraftVarInt.write(body, payload.length);
                body.writeBytes(deflate(payload));
            } else {
                MinecraftVarInt.write(body, 0);
                body.writeBytes(payload);
            }
        } else {
            body.writeBytes(payload);
        }
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        MinecraftVarInt.write(frame, body.size());
        frame.writeBytes(body.toByteArray());
        return frame.toByteArray();
    }

    private static byte[] deflate(byte[] payload) {
        Deflater deflater = new Deflater();
        deflater.setInput(payload);
        deflater.finish();
        byte[] buffer = new byte[payload.length + 64];
        int length = deflater.deflate(buffer);
        deflater.end();
        return java.util.Arrays.copyOf(buffer, length);
    }

    private static byte[] inflate(byte[] payload, int expectedLength) throws java.util.zip.DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(payload);
        byte[] result = new byte[expectedLength];
        int length = inflater.inflate(result);
        inflater.end();
        return length == expectedLength ? result : java.util.Arrays.copyOf(result, length);
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        MinecraftVarInt.write(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static String readString(ByteBuf payload) {
        int length = MinecraftVarInt.read(payload);
        byte[] bytes = new byte[length];
        payload.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) ((value >>> shift) & 0xff));
        }
    }

    private static void writeDouble(ByteArrayOutputStream out, double value) {
        writeLong(out, Double.doubleToLongBits(value));
    }

    private static void writeFloat(ByteArrayOutputStream out, float value) {
        int bits = Float.floatToIntBits(value);
        out.write((bits >>> 24) & 0xff);
        out.write((bits >>> 16) & 0xff);
        out.write((bits >>> 8) & 0xff);
        out.write(bits & 0xff);
    }

    private final class ProtocolHandler extends ChannelInboundHandlerAdapter {
        private ByteBuf pending = Unpooled.buffer();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
            if (!(message instanceof ByteBuf incoming)) {
                return;
            }
            pending.writeBytes(incoming);
            incoming.release();
            while (pending.isReadable()) {
                pending.markReaderIndex();
                int length;
                try {
                    length = MinecraftVarInt.read(pending);
                } catch (IndexOutOfBoundsException e) {
                    pending.resetReaderIndex();
                    return;
                }
                if (pending.readableBytes() < length) {
                    pending.resetReaderIndex();
                    return;
                }
                ByteBuf packet = pending.readBytes(length);
                try {
                    handlePacket(ctx, packet);
                } catch (Exception e) {
                    LOGGER.debug("Loopback packet ignored user={} cause={}", username, e.getClass().getSimpleName());
                } finally {
                    packet.release();
                }
            }
            if (pending.readerIndex() > 0) {
                pending.discardReadBytes();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            LOGGER.debug("Loopback channel inactive user={}", username);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.debug("Loopback exception ignored user={} cause={}", username, cause.getClass().getSimpleName());
        }

        private void handlePacket(ChannelHandlerContext ctx, ByteBuf frame) throws Exception {
            ByteBuf payload = frame;
            if (compressionThreshold >= 0) {
                int uncompressedLength = MinecraftVarInt.read(frame);
                if (uncompressedLength > 0) {
                    byte[] compressed = new byte[frame.readableBytes()];
                    frame.readBytes(compressed);
                    payload = Unpooled.wrappedBuffer(inflate(compressed, uncompressedLength));
                }
            }
            int packetId = MinecraftVarInt.read(payload);
            if (!loginComplete) {
                handleLoginPacket(ctx, packetId, payload);
            } else {
                handlePlayPacket(ctx, packetId, payload);
            }
            if (payload != frame) {
                payload.release();
            }
        }

        private void handleLoginPacket(ChannelHandlerContext ctx, int packetId, ByteBuf payload) {
            switch (packetId) {
                case 0x01 -> throw new IllegalStateException("online-mode encryption is unsupported");
                case 0x02 -> loginComplete = true;
                case 0x03 -> compressionThreshold = MinecraftVarInt.read(payload);
                case 0x04 -> {
                    int transactionId = MinecraftVarInt.read(payload);
                    ForgeLoginMessage forgeMessage = readForgeLoginMessage(payload);
                    if (forgeMessage.handshake()) {
                        byte[] response = switch (forgeMessage.discriminator()) {
                            case 1 -> forgeModListReplyFrame(
                                        transactionId,
                                        forgeMessage.channels(),
                                        compressionThreshold >= 0,
                                        compressionThreshold);
                            case 3, 4, 6 -> forgeHandshakeAckFrame(
                                    transactionId,
                                    compressionThreshold >= 0,
                                    compressionThreshold);
                            default -> null;
                        };
                        if (response != null) {
                            ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
                        }
                    } else {
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(
                                loginPluginResponseFrame(transactionId, compressionThreshold >= 0, compressionThreshold)));
                    }
                }
                default -> {
                }
            }
        }

        private void handlePlayPacket(ChannelHandlerContext ctx, int packetId, ByteBuf payload) {
            if (packetId == 0x26) {
                connected = true;
                ctx.writeAndFlush(Unpooled.wrappedBuffer(clientInformationFrame(compressionThreshold >= 0, compressionThreshold)));
            } else if (packetId == 0x21) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(
                        keepAliveResponseFrame(payload.readLong(), compressionThreshold >= 0, compressionThreshold)));
            } else if (packetId == 0x38) {
                payload.skipBytes(8 + 8 + 8 + 4 + 4 + 1);
                int teleportId = MinecraftVarInt.read(payload);
                ctx.writeAndFlush(Unpooled.wrappedBuffer(
                        teleportAcceptFrame(teleportId, compressionThreshold >= 0, compressionThreshold)));
            }
        }
    }

    private static ForgeLoginMessage readForgeLoginMessage(ByteBuf payload) {
        if (!payload.isReadable()) {
            return ForgeLoginMessage.notForge();
        }
        String channel = readString(payload);
        if (!"fml:loginwrapper".equals(channel) || !payload.isReadable()) {
            return ForgeLoginMessage.notForge();
        }
        String wrappedChannel = readString(payload);
        int wrappedLength = MinecraftVarInt.read(payload);
        ByteBuf wrapped = payload.readBytes(Math.min(wrappedLength, payload.readableBytes()));
        try {
            int discriminator = wrapped.isReadable() ? MinecraftVarInt.read(wrapped) : -1;
            Map<String, String> channels = discriminator == 1
                    ? readServerChannelVersions(wrapped)
                    : defaultForgeChannels();
            return new ForgeLoginMessage("fml:handshake".equals(wrappedChannel), discriminator, channels);
        } finally {
            wrapped.release();
        }
    }

    private static Map<String, String> readServerChannelVersions(ByteBuf payload) {
        try {
            int modCount = MinecraftVarInt.read(payload);
            for (int i = 0; i < modCount; i++) {
                readString(payload);
            }
            int channelCount = MinecraftVarInt.read(payload);
            Map<String, String> channels = new LinkedHashMap<>();
            for (int i = 0; i < channelCount; i++) {
                channels.put(readString(payload), readString(payload));
            }
            return channels.isEmpty() ? defaultForgeChannels() : channels;
        } catch (RuntimeException e) {
            return defaultForgeChannels();
        }
    }

    private static Map<String, String> defaultForgeChannels() {
        Map<String, String> channels = new LinkedHashMap<>();
        channels.put("fml:handshake", "FML3");
        channels.put("fml:play", "FML3");
        channels.put("fml:loginwrapper", "FML3");
        return channels;
    }

    private record ForgeLoginMessage(boolean handshake, int discriminator, Map<String, String> channels) {
        static ForgeLoginMessage notForge() {
            return new ForgeLoginMessage(false, -1, Map.of());
        }
    }
}
