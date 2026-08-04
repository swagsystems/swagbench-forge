package com.virgoagario.swagbench.load;

import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;

public final class MinecraftVarInt {
    private MinecraftVarInt() {
    }

    public static byte[] encode(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5);
        int current = value;
        do {
            byte temp = (byte) (current & 0b0111_1111);
            current >>>= 7;
            if (current != 0) {
                temp |= (byte) 0b1000_0000;
            }
            out.write(temp);
        } while (current != 0);
        return out.toByteArray();
    }

    public static int read(ByteBuf buffer) {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = buffer.readByte();
            value |= (current & 0b0111_1111) << position;
            position += 7;
            if (position >= 35) {
                throw new IllegalArgumentException("VarInt is too large");
            }
        } while ((current & 0b1000_0000) != 0);
        return value;
    }

    public static void write(ByteArrayOutputStream out, int value) {
        out.writeBytes(encode(value));
    }
}
