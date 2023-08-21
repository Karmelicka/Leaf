// Gale - Velocity - VarInt and VarLong optimizations

package net.minecraft.network;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VarIntLongTest {

    private static String padStringWithLeadingZeros(String string, int length) {
        if (string.length() >= length) {
            return string;
        }
        return "0".repeat(length - string.length()) + string;
    }

    private static final IntSet integerCases;
    static {
        integerCases = new IntOpenHashSet();
        {
            integerCases.add(0);
            integerCases.add(-1);
        }
        {
            for (int i = 0; i < 32; i++) {
                integerCases.add(1 << i);
            }
        }
        {
            for (int factor = 1; factor <= 7; factor++) {
                int all = 0;
                for (int i = 0; i <= 4; i++) {
                    int shifted = 1 << (i * factor);
                    all |= shifted;
                    integerCases.add(shifted);
                    integerCases.add(shifted - 1);
                    integerCases.add(~shifted);
                    integerCases.add(-shifted);
                    integerCases.add((~shifted) & (0x80000000));
                    integerCases.add(all);
                    integerCases.add(all - 1);
                    integerCases.add(~all);
                    integerCases.add(-all);
                    integerCases.add((~all) & (0x80000000));
                }
            }
        }
        {
            var newCases = new IntOpenHashSet();
            for (int shiftSize = 2; shiftSize <= 6; shiftSize++) {
                for (int offset = 0; offset < shiftSize; offset++) {
                    int striped = 0;
                    for (int i = offset; i < 32; i += shiftSize) {
                        striped |= 1 << i;
                    }
                    final var finalStriped = striped;
                    integerCases.forEach(existing -> {
                        newCases.add(existing | finalStriped);
                        newCases.add(existing | (~finalStriped));
                        newCases.add(existing & finalStriped);
                        newCases.add(existing & (~finalStriped));
                        newCases.add(existing - finalStriped);
                        newCases.add(existing - (~finalStriped));
                    });
                }
            }
            integerCases.addAll(newCases);
        }
    }

    private static final LongSet longCases;
    static {
        longCases = new LongOpenHashSet();
        {
            longCases.add(0);
            longCases.add(-1);
        }
        {
            for (int i = 0; i < 64; i++) {
                longCases.add(1L << i);
            }
        }
        {
            for (int factor = 1; factor <= 7; factor++) {
                long all = 0;
                for (int i = 0; i <= 9; i++) {
                    long shifted = 1L << (i * factor);
                    all |= shifted;
                    longCases.add(shifted);
                    longCases.add(shifted - 1);
                    longCases.add(~shifted);
                    longCases.add(-shifted);
                    longCases.add((~shifted) & (0x8000000000000000L));
                    longCases.add(all);
                    longCases.add(all - 1);
                    longCases.add(~all);
                    longCases.add(-all);
                    longCases.add((~all) & (0x8000000000000000L));
                }
            }
        }
        {
            var newCases = new LongOpenHashSet();
            for (int shiftSize = 2; shiftSize <= 6; shiftSize++) {
                for (int offset = 0; offset < shiftSize; offset++) {
                    long striped = 0;
                    for (int i = offset; i < 64; i += shiftSize) {
                        striped |= 1L << i;
                    }
                    final var finalStriped = striped;
                    longCases.forEach(existing -> {
                        newCases.add(existing | finalStriped);
                        newCases.add(existing | (~finalStriped));
                        newCases.add(existing & finalStriped);
                        newCases.add(existing & (~finalStriped));
                        newCases.add(existing - finalStriped);
                        newCases.add(existing - (~finalStriped));
                    });
                }
            }
            longCases.addAll(newCases);
        }
    }

    // Gale start - Velocity - pre-compute VarInt and VarLong sizes
    @Test
    public void testGetVarIntSizeComparedToOld() {
        integerCases.forEach(value -> {
            // given
            int originalSize = VarInt.getByteSizeOld(value);

            // when
            int size = VarInt.getByteSize(value);

            // then
            Assertions.assertEquals(Float.parseFloat("Optimized size (" + size + ") is not equal to original size (" + originalSize + ") for test case value " + value + " (binary: " + padStringWithLeadingZeros(Integer.toBinaryString(value), 32) + ")"), originalSize, size);
        });
    }

    @Test
    public void testGetVarLongSizeComparedToOriginal() {
        longCases.forEach(value -> {
            // given
            int originalSize = VarLong.getByteSizeOriginal(value);

            // when
            int size = VarLong.getByteSize(value);

            // then
            Assertions.assertEquals(Float.parseFloat("Optimized size (" + size + ") is not equal to original size (" + originalSize + ") for test case value " + value + " (binary: " + padStringWithLeadingZeros(Long.toBinaryString(value), 64) + ")"), originalSize, size);
        });
    }
    // Gale end - Velocity - pre-compute VarInt and VarLong sizes

}
