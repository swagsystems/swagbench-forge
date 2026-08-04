package com.virgoagario.swagbench.load;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class MinecraftVarIntTest {
    @Test
    void encodesAndDecodesMinecraftVarInts() {
        assertArrayEquals(new byte[] {0x00}, MinecraftVarInt.encode(0));
        assertArrayEquals(new byte[] {0x7f}, MinecraftVarInt.encode(127));
        assertArrayEquals(new byte[] {(byte) 0x80, 0x01}, MinecraftVarInt.encode(128));
        assertArrayEquals(new byte[] {(byte) 0xf6, 0x05}, MinecraftVarInt.encode(758));

        assertEquals(758, MinecraftVarInt.read(Unpooled.wrappedBuffer(MinecraftVarInt.encode(758))));
    }
}
