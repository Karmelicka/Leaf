package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class VarLong {
    private static final int MAX_VARLONG_SIZE = 10;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    // Gale start - Velocity - pre-compute VarInt and VarLong sizes
    private static final int[] EXACT_BYTE_LENGTHS = new int[65];
    static {
        for (int i = 0; i < 64; ++i) {
            EXACT_BYTE_LENGTHS[i] = (64 - i + 6) / 7;
        }
        EXACT_BYTE_LENGTHS[64] = 1; // Special case for the number 0
    }
    // Gale end - Velocity - pre-compute VarInt and VarLong sizes
    public static int getByteSize(long l) {
        // Gale start - Velocity - pre-compute VarInt and VarLong sizes
        return EXACT_BYTE_LENGTHS[Long.numberOfLeadingZeros(l)];
    }

    static int getByteSizeOriginal(long l) { // public -> package-private
        // Gale end - Velocity - pre-compute VarInt and VarLong sizes
        for(int i = 1; i < 10; ++i) {
            if ((l & -1L << i * 7) == 0L) {
                return i;
            }
        }

        return 10;
    }

    public static boolean hasContinuationBit(byte b) {
        return (b & 128) == 128;
    }

    public static long read(ByteBuf buf) {
        long l = 0L;
        int i = 0;

        byte b;
        do {
            b = buf.readByte();
            l |= (long)(b & 127) << i++ * 7;
            if (i > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while(hasContinuationBit(b));

        return l;
    }

    public static ByteBuf write(ByteBuf buf, long l) {
        while((l & -128L) != 0L) {
            buf.writeByte((int)(l & 127L) | 128);
            l >>>= 7;
        }

        buf.writeByte((int)l);
        return buf;
    }
}
