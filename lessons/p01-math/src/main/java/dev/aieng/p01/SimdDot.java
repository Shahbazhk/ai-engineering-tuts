package dev.aieng.p01;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Dot product with the Vector API (Project Panama). One instruction multiplies 4, 8 or 16 floats at once,
 * depending on the CPU (SSE, AVX2, AVX-512, NEON). Pure Java LLM engines such as Jlama are built on this.
 * Requires {@code --add-modules jdk.incubator.vector} at compile and run time.
 */
public final class SimdDot {

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SimdDot() {
    }

    public static float scalar(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    // region simd
    public static float simd(float[] a, float[] b) {
        FloatVector acc = FloatVector.zero(SPECIES);
        int i = 0;
        int upper = SPECIES.loopBound(a.length);
        for (; i < upper; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            acc = va.fma(vb, acc); // acc += va * vb, lane by lane, in one fused instruction
        }
        float sum = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) { // scalar tail for lengths that are not a multiple of the lane count
            sum += a[i] * b[i];
        }
        return sum;
    }
    // endregion

    public static int lanes() {
        return SPECIES.length();
    }

    /** Crude benchmark; use JMH for real measurements (warm-up, dead-code elimination and forking matter). */
    public static void main(String[] args) {
        int n = 1 << 20;
        float[] a = new float[n];
        float[] b = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        }
        for (int round = 0; round < 5; round++) {
            long t0 = System.nanoTime();
            float s1 = scalar(a, b);
            long t1 = System.nanoTime();
            float s2 = simd(a, b);
            long t2 = System.nanoTime();
            System.out.printf("lanes=%d scalar=%.2fms simd=%.2fms (%.1f vs %.1f)%n", lanes(),
                    (t1 - t0) / 1e6, (t2 - t1) / 1e6, s1, s2);
        }
    }
}
