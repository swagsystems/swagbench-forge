package com.virgoagario.swagbench.load;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LoopbackClientProtocolTest {
    @Test
    void buildsProtocol758HandshakeFrame() {
        byte[] frame = LoopbackClient.handshakeFrame("127.0.0.1", 25565);

        ByteBuf payload = unwrapFrame(frame);
        assertEquals(0x00, MinecraftVarInt.read(payload));
        assertEquals(758, MinecraftVarInt.read(payload));
        assertEquals("127.0.0.1\0FML3\0", readString(payload));
        assertEquals(25565, payload.readUnsignedShort());
        assertEquals(2, MinecraftVarInt.read(payload));
    }

    @Test
    void buildsOfflineLoginStartFrame() {
        byte[] frame = LoopbackClient.loginStartFrame("swagbench-0");

        ByteBuf payload = unwrapFrame(frame);
        assertEquals(0x00, MinecraftVarInt.read(payload));
        assertEquals("swagbench-0", readString(payload));
    }

    @Test
    void repliesToKeepAliveAndTeleportPackets() {
        assertArrayEquals(
                new byte[] {0x09, 0x0f, 0, 0, 0, 0, 0, 0, 0, 42},
                LoopbackClient.keepAliveResponseFrame(42L, false, 256));

        ByteBuf teleport = unwrapFrame(LoopbackClient.teleportAcceptFrame(17, false, 256));
        assertEquals(0x00, MinecraftVarInt.read(teleport));
        assertEquals(17, MinecraftVarInt.read(teleport));
    }

    @Test
    void wrapsForgeHandshakeAcknowledgementsForLoginCustomQueries() {
        ByteBuf ack = unwrapFrame(LoopbackClient.forgeHandshakeAckFrame(11, false, 256));

        assertEquals(0x02, MinecraftVarInt.read(ack));
        assertEquals(11, MinecraftVarInt.read(ack));
        assertEquals(1, ack.readUnsignedByte());
        assertEquals("fml:handshake", readString(ack));
        int wrappedLength = MinecraftVarInt.read(ack);
        ByteBuf wrapped = ack.readBytes(wrappedLength);
        assertEquals(99, MinecraftVarInt.read(wrapped));
    }

    @Test
    void wrapsForgeModListReplyForLoginCustomQueries() {
        ByteBuf reply = unwrapFrame(LoopbackClient.forgeModListReplyFrame(12, false, 256));

        assertEquals(0x02, MinecraftVarInt.read(reply));
        assertEquals(12, MinecraftVarInt.read(reply));
        assertEquals(1, reply.readUnsignedByte());
        assertEquals("fml:handshake", readString(reply));
        int wrappedLength = MinecraftVarInt.read(reply);
        ByteBuf wrapped = reply.readBytes(wrappedLength);
        assertEquals(2, MinecraftVarInt.read(wrapped));
        assertEquals(3, MinecraftVarInt.read(wrapped));
        assertEquals("minecraft", readString(wrapped));
        assertEquals("forge", readString(wrapped));
        assertEquals("swagbench", readString(wrapped));
        assertEquals(3, MinecraftVarInt.read(wrapped));
        assertEquals("fml:handshake", readString(wrapped));
        assertEquals("FML3", readString(wrapped));
    }

    private static ByteBuf unwrapFrame(byte[] frame) {
        ByteBuf wrapped = Unpooled.wrappedBuffer(frame);
        int length = MinecraftVarInt.read(wrapped);
        return wrapped.readBytes(length);
    }

    private static String readString(ByteBuf payload) {
        int length = MinecraftVarInt.read(payload);
        byte[] bytes = new byte[length];
        payload.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
