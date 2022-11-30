package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class ImprovedNoise {
    private static final float SHIFT_UP_EPSILON = 1.0E-7F;
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    // Gale start - C2ME - optimize noise generation
    private static final double[] FLAT_SIMPLEX_GRAD = new double[]{
        1, 1, 0, 0,
        -1, 1, 0, 0,
        1, -1, 0, 0,
        -1, -1, 0, 0,
        1, 0, 1, 0,
        -1, 0, 1, 0,
        1, 0, -1, 0,
        -1, 0, -1, 0,
        0, 1, 1, 0,
        0, -1, 1, 0,
        0, 1, -1, 0,
        0, -1, -1, 0,
        1, 1, 0, 0,
        0, -1, 1, 0,
        -1, 1, 0, 0,
        0, -1, -1, 0,
    };
    // Gale end - C2ME - optimize noise generation

    public ImprovedNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0D;
        this.yo = random.nextDouble() * 256.0D;
        this.zo = random.nextDouble() * 256.0D;
        this.p = new byte[256];

        for(int i = 0; i < 256; ++i) {
            this.p[i] = (byte)i;
        }

        for(int j = 0; j < 256; ++j) {
            int k = random.nextInt(256 - j);
            byte b = this.p[j];
            this.p[j] = this.p[j + k];
            this.p[j + k] = b;
        }

    }

    public double noise(double x, double y, double z) {
        return this.noise(x, y, z, 0.0D, 0.0D);
    }

    /** @deprecated */
    @Deprecated
    public double noise(double x, double y, double z, double yScale, double yMax) {
        double d = x + this.xo;
        double e = y + this.yo;
        double f = z + this.zo;
        // Gale start - C2ME - optimize noise generation - optimize: remove frequent type conversions
        double i = Math.floor(d);
        double j = Math.floor(e);
        double k = Math.floor(f);
        double g = d - i;
        double h = e - j;
        double l = f - k;
        // Gale end - C2ME - optimize noise generation - optimize: remove frequent type conversions
        double o;
        if (yScale != 0.0D) {
            double m;
            if (yMax >= 0.0D && yMax < h) {
                m = yMax;
            } else {
                m = h;
            }

            o = Math.floor(m / yScale + (double)1.0E-7F) * yScale; // Gale - C2ME - optimize noise generation - optimize: remove frequent type conversions
        } else {
            o = 0.0D;
        }

        return this.sampleAndLerp((int) i, (int) j, (int) k, g, h - o, l, h); // Gale - C2ME - optimize noise generation - optimize: remove frequent type conversions
    }

    public double noiseWithDerivative(double x, double y, double z, double[] ds) {
        double d = x + this.xo;
        double e = y + this.yo;
        double f = z + this.zo;
        // Gale start - C2ME - optimize noise generation - optimize: remove frequent type conversions
        double i = Math.floor(d);
        double j = Math.floor(e);
        double k = Math.floor(f);
        double g = d - i;
        double h = e - j;
        double l = f - k;
        return this.sampleWithDerivative((int) i, (int) j, (int) k, g, h, l, ds);
        // Gale end - C2ME - optimize noise generation - optimize: remove frequent type conversions
    }

    private static double gradDot(int hash, double x, double y, double z) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[hash & 15], x, y, z);
    }

    private int p(int input) {
        return this.p[input & 255] & 255;
    }

    private double sampleAndLerp(int sectionX, int sectionY, int sectionZ, double localX, double localY, double localZ, double fadeLocalY) {
        // Gale start - C2ME - optimize noise generation - inline math & small optimization: remove frequent type conversions and redundant ops
        final int var0 = sectionX & 0xFF;
        final int var1 = (sectionX + 1) & 0xFF;
        final int var2 = this.p[var0] & 0xFF;
        final int var3 = this.p[var1] & 0xFF;
        final int var4 = (var2 + sectionY) & 0xFF;
        final int var5 = (var3 + sectionY) & 0xFF;
        final int var6 = (var2 + sectionY + 1) & 0xFF;
        final int var7 = (var3 + sectionY + 1) & 0xFF;
        final int var8 = this.p[var4] & 0xFF;
        final int var9 = this.p[var5] & 0xFF;
        final int var10 = this.p[var6] & 0xFF;
        final int var11 = this.p[var7] & 0xFF;

        final int var12 = (var8 + sectionZ) & 0xFF;
        final int var13 = (var9 + sectionZ) & 0xFF;
        final int var14 = (var10 + sectionZ) & 0xFF;
        final int var15 = (var11 + sectionZ) & 0xFF;
        final int var16 = (var8 + sectionZ + 1) & 0xFF;
        final int var17 = (var9 + sectionZ + 1) & 0xFF;
        final int var18 = (var10 + sectionZ + 1) & 0xFF;
        final int var19 = (var11 + sectionZ + 1) & 0xFF;
        final int var20 = (this.p[var12] & 15) << 2;
        final int var21 = (this.p[var13] & 15) << 2;
        final int var22 = (this.p[var14] & 15) << 2;
        final int var23 = (this.p[var15] & 15) << 2;
        final int var24 = (this.p[var16] & 15) << 2;
        final int var25 = (this.p[var17] & 15) << 2;
        final int var26 = (this.p[var18] & 15) << 2;
        final int var27 = (this.p[var19] & 15) << 2;
        final double var60 = localX - 1.0;
        final double var61 = localY - 1.0;
        final double var62 = localZ - 1.0;
        final double var87 = FLAT_SIMPLEX_GRAD[(var20) | 0] * localX + FLAT_SIMPLEX_GRAD[(var20) | 1] * localY + FLAT_SIMPLEX_GRAD[(var20) | 2] * localZ;
        final double var88 = FLAT_SIMPLEX_GRAD[(var21) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var21) | 1] * localY + FLAT_SIMPLEX_GRAD[(var21) | 2] * localZ;
        final double var89 = FLAT_SIMPLEX_GRAD[(var22) | 0] * localX + FLAT_SIMPLEX_GRAD[(var22) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var22) | 2] * localZ;
        final double var90 = FLAT_SIMPLEX_GRAD[(var23) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var23) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var23) | 2] * localZ;
        final double var91 = FLAT_SIMPLEX_GRAD[(var24) | 0] * localX + FLAT_SIMPLEX_GRAD[(var24) | 1] * localY + FLAT_SIMPLEX_GRAD[(var24) | 2] * var62;
        final double var92 = FLAT_SIMPLEX_GRAD[(var25) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var25) | 1] * localY + FLAT_SIMPLEX_GRAD[(var25) | 2] * var62;
        final double var93 = FLAT_SIMPLEX_GRAD[(var26) | 0] * localX + FLAT_SIMPLEX_GRAD[(var26) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var26) | 2] * var62;
        final double var94 = FLAT_SIMPLEX_GRAD[(var27) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var27) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var27) | 2] * var62;

        final double var95 = localX * 6.0 - 15.0;
        final double var96 = fadeLocalY * 6.0 - 15.0;
        final double var97 = localZ * 6.0 - 15.0;
        final double var98 = localX * var95 + 10.0;
        final double var99 = fadeLocalY * var96 + 10.0;
        final double var100 = localZ * var97 + 10.0;
        final double var101 = localX * localX * localX * var98;
        final double var102 = fadeLocalY * fadeLocalY * fadeLocalY * var99;
        final double var103 = localZ * localZ * localZ * var100;

        final double var113 = var87 + var101 * (var88 - var87);
        final double var114 = var93 + var101 * (var94 - var93);
        final double var115 = var91 + var101 * (var92 - var91);
        final double var116 = var89 + var101 * (var90 - var89);
        final double var117 = var114 - var115;
        final double var118 = var102 * (var116 - var113);
        final double var119 = var102 * var117;
        final double var120 = var113 + var118;
        final double var121 = var115 + var119;
        return var120 + (var103 * (var121 - var120));
        // Gale end - C2ME - optimize noise generation - inline math & small optimization: remove frequent type conversions and redundant ops
    }

    private double sampleWithDerivative(int sectionX, int sectionY, int sectionZ, double localX, double localY, double localZ, double[] ds) {
        int i = this.p(sectionX);
        int j = this.p(sectionX + 1);
        int k = this.p(i + sectionY);
        int l = this.p(i + sectionY + 1);
        int m = this.p(j + sectionY);
        int n = this.p(j + sectionY + 1);
        int o = this.p(k + sectionZ);
        int p = this.p(m + sectionZ);
        int q = this.p(l + sectionZ);
        int r = this.p(n + sectionZ);
        int s = this.p(k + sectionZ + 1);
        int t = this.p(m + sectionZ + 1);
        int u = this.p(l + sectionZ + 1);
        int v = this.p(n + sectionZ + 1);
        int[] is = SimplexNoise.GRADIENT[o & 15];
        int[] js = SimplexNoise.GRADIENT[p & 15];
        int[] ks = SimplexNoise.GRADIENT[q & 15];
        int[] ls = SimplexNoise.GRADIENT[r & 15];
        int[] ms = SimplexNoise.GRADIENT[s & 15];
        int[] ns = SimplexNoise.GRADIENT[t & 15];
        int[] os = SimplexNoise.GRADIENT[u & 15];
        int[] ps = SimplexNoise.GRADIENT[v & 15];
        double d = SimplexNoise.dot(is, localX, localY, localZ);
        double e = SimplexNoise.dot(js, localX - 1.0D, localY, localZ);
        double f = SimplexNoise.dot(ks, localX, localY - 1.0D, localZ);
        double g = SimplexNoise.dot(ls, localX - 1.0D, localY - 1.0D, localZ);
        double h = SimplexNoise.dot(ms, localX, localY, localZ - 1.0D);
        double w = SimplexNoise.dot(ns, localX - 1.0D, localY, localZ - 1.0D);
        double x = SimplexNoise.dot(os, localX, localY - 1.0D, localZ - 1.0D);
        double y = SimplexNoise.dot(ps, localX - 1.0D, localY - 1.0D, localZ - 1.0D);
        double z = Mth.smoothstep(localX);
        double aa = Mth.smoothstep(localY);
        double ab = Mth.smoothstep(localZ);
        double ac = Mth.lerp3(z, aa, ab, (double)is[0], (double)js[0], (double)ks[0], (double)ls[0], (double)ms[0], (double)ns[0], (double)os[0], (double)ps[0]);
        double ad = Mth.lerp3(z, aa, ab, (double)is[1], (double)js[1], (double)ks[1], (double)ls[1], (double)ms[1], (double)ns[1], (double)os[1], (double)ps[1]);
        double ae = Mth.lerp3(z, aa, ab, (double)is[2], (double)js[2], (double)ks[2], (double)ls[2], (double)ms[2], (double)ns[2], (double)os[2], (double)ps[2]);
        double af = Mth.lerp2(aa, ab, e - d, g - f, w - h, y - x);
        double ag = Mth.lerp2(ab, z, f - d, x - h, g - e, y - w);
        double ah = Mth.lerp2(z, aa, h - d, w - e, x - f, y - g);
        double ai = Mth.smoothstepDerivative(localX);
        double aj = Mth.smoothstepDerivative(localY);
        double ak = Mth.smoothstepDerivative(localZ);
        double al = ac + ai * af;
        double am = ad + aj * ag;
        double an = ae + ak * ah;
        ds[0] += al;
        ds[1] += am;
        ds[2] += an;
        return Mth.lerp3(z, aa, ab, d, e, f, g, h, w, x, y);
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder info) {
        NoiseUtils.parityNoiseOctaveConfigString(info, this.xo, this.yo, this.zo, this.p);
    }
}
