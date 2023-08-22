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
        // Gale start - Velocity - optimized VarLong#write
        if ((l & 0xFFFFFFFFFFFFFF80L) == 0) {
            buf.writeByte((int) l);
        } else if (l < 0) {
            // The case of writing arbitrary longs is common
            // Here, the number is negative, which has probability 1/2 for arbitrary numbers
            int least7bits = (int) (l & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            long nonLeast7Bits = l >>> 28;
            int secondLeast7bits = (int) (nonLeast7Bits & 0xFFFFFFFL);
            int w2 = (secondLeast7bits & 0x7F) << 24
                | ((secondLeast7bits & 0x3F80) << 9)
                | (secondLeast7bits & 0x1FC000) >> 6
                | (secondLeast7bits >>> 21)
                | 0x80808080;
            int thirdLeast7Bits = (int) (nonLeast7Bits >>> 28);
            int w3 = (thirdLeast7Bits & 0x7F) << 8
                | (thirdLeast7Bits >>> 7)
                | 0x00008000;
            buf.writeInt(w);
            buf.writeInt(w2);
            buf.writeShort(w3);
        } else if ((l & 0xFFFFFFFFFFFFC000L) == 0) {
            int least7bits = (int) l;
            int w = (least7bits & 0x7F) << 8
                | (least7bits >>> 7)
                | 0x00008000;
            buf.writeShort(w);
        } else if ((l & 0xFFFFFFFFFFE00000L) == 0) {
            int least7bits = (int) l;
            int w = (least7bits & 0x7F) << 16
                | (least7bits & 0x3F80) << 1
                | (least7bits >>> 14)
                | 0x00808000;
            buf.writeMedium(w);
        } else if ((l & 0xFFFFFFFFF0000000L) == 0) {
            int least7bits = (int) l;
            int w = (least7bits & 0x7F) << 24
                | ((least7bits & 0x3F80) << 9)
                | (least7bits & 0x1FC000) >> 6
                | (least7bits >>> 21)
                | 0x80808000;
            buf.writeInt(w);
        } else if ((l & 0xFFFFFFF800000000L) == 0) {
            int least7bits = (int) (l & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            buf.writeInt(w);
            buf.writeByte((int) (l >>> 28));
        } else if ((l & 0xFFFFFC0000000000L) == 0) {
            int least7bits = (int) (l & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            int secondLeast7bits = (int) (l >>> 28);
            int w2 = (secondLeast7bits & 0x7F) << 8
                | (secondLeast7bits >>> 7)
                | 0x00008000;
            buf.writeInt(w);
            buf.writeShort(w2);
        } else if ((l & 0xFFFE000000000000L) == 0) {
            int least7bits = (int) (l & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            int secondLeast7bits = (int) (l >>> 28);
            int w2 = (secondLeast7bits & 0x7F) << 16
                | (secondLeast7bits & 0x3F80) << 1
                | (secondLeast7bits >>> 14)
                | 0x00808000;
            buf.writeInt(w);
            buf.writeMedium(w2);
        } else if ((l & 0xFF00000000000000L) == 0) {
            int least7bits = (int) (l & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            int secondLeast7bits = (int) (l >>> 28);
            int w2 = (secondLeast7bits & 0x7F) << 24
                | ((secondLeast7bits & 0x3F80) << 9)
                | (secondLeast7bits & 0x1FC000) >> 6
                | (secondLeast7bits >>> 21)
                | 0x80808000;
            buf.writeInt(w);
            buf.writeInt(w2);
        } else {
            int least7bits = (int) (l & 0xFFFFFFFL);
            int w = (least7bits & 0x7F) << 24
                | (least7bits & 0x3F80) << 9
                | (least7bits & 0x1FC000) >> 6
                | ((least7bits >>> 21) & 0x7F)
                | 0x80808080;
            long nonLeast7Bits = l >>> 28;
            int secondLeast7bits = (int) (nonLeast7Bits & 0xFFFFFFFL);
            int w2 = (secondLeast7bits & 0x7F) << 24
                | ((secondLeast7bits & 0x3F80) << 9)
                | (secondLeast7bits & 0x1FC000) >> 6
                | (secondLeast7bits >>> 21)
                | 0x80808080;
            buf.writeInt(w);
            buf.writeInt(w2);
            buf.writeByte((int) (nonLeast7Bits >>> 28));
        }
        return buf;
    }

    static ByteBuf writeOriginal(ByteBuf buf, long l) { // public -> package-private
        // Gale end - Velocity - optimized VarLong#write
        while((l & -128L) != 0L) {
            buf.writeByte((int)(l & 127L) | 128);
            l >>>= 7;
        }

        buf.writeByte((int)l);
        return buf;
    }
}
