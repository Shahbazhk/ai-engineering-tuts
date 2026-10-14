package dev.aieng.p13;

/**
 * Weight quantisation: store weights in fewer bits with a scale factor, dequantise on the fly. Because LLM decoding
 * is memory-bandwidth bound (Phase 5), smaller weights mean faster tokens as well as less memory.
 */
public final class Quantize {

    /** Quantised block: integer codes plus one float scale per block of values. */
    public record Blocks(byte[] codes, float[] scales, int blockSize, int bits) {
        public long bytes() {
            return (long) Math.ceil(codes.length * bits / 8.0) + scales.length * 2L; // scales stored as fp16
        }
    }

    private Quantize() {
    }

    // region quantize
    /**
     * Symmetric block-wise quantisation (the idea behind GGUF Q8_0 / Q4_0): for each block of {@code blockSize}
     * values, scale = max|x| / (2^(bits-1) - 1), code = round(x / scale). Small blocks track local magnitude, so one
     * outlier only hurts its own block.
     */
    public static Blocks quantize(float[] weights, int blockSize, int bits) {
        int levels = (1 << (bits - 1)) - 1; // 127 for 8-bit, 7 for 4-bit
        int nBlocks = (weights.length + blockSize - 1) / blockSize;
        byte[] codes = new byte[weights.length];
        float[] scales = new float[nBlocks];
        for (int blk = 0; blk < nBlocks; blk++) {
            int from = blk * blockSize;
            int to = Math.min(weights.length, from + blockSize);
            float max = 0;
            for (int i = from; i < to; i++) {
                max = Math.max(max, Math.abs(weights[i]));
            }
            float scale = max == 0 ? 1 : max / levels;
            scales[blk] = scale;
            for (int i = from; i < to; i++) {
                codes[i] = (byte) Math.max(-levels, Math.min(levels, Math.round(weights[i] / scale)));
            }
        }
        return new Blocks(codes, scales, blockSize, bits);
    }

    public static float[] dequantize(Blocks q) {
        float[] out = new float[q.codes().length];
        for (int i = 0; i < out.length; i++) {
            out[i] = q.codes()[i] * q.scales()[i / q.blockSize()];
        }
        return out;
    }
    // endregion

    /** Root-mean-square error relative to the RMS of the original weights. */
    public static double relativeError(float[] original, float[] restored) {
        double err = 0;
        double norm = 0;
        for (int i = 0; i < original.length; i++) {
            err += (original[i] - restored[i]) * (original[i] - restored[i]);
            norm += original[i] * original[i];
        }
        return Math.sqrt(err / norm);
    }

    public static void main(String[] args) {
        java.util.Random rnd = new java.util.Random(1);
        float[] w = new float[1 << 20];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (rnd.nextGaussian() * 0.02);
        }
        w[12345] = 1.5f; // a typical outlier weight
        System.out.printf("fp32: %,d bytes%n", w.length * 4L);
        for (int[] cfg : new int[][] {{8, 32}, {4, 32}, {4, 1 << 20}}) {
            Blocks q = quantize(w, cfg[1], cfg[0]);
            System.out.printf("%d-bit, block %-7d: %,10d bytes, relative error %.4f%n", cfg[0], cfg[1], q.bytes(),
                    relativeError(w, dequantize(q)));
        }
    }
}
