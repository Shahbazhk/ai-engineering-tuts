package dev.aieng.p13;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class FineTuningTest {

    @Test
    void loraStartsAtTheBaseModelAndLearnsALowRankChange() {
        int d = 64;
        Random rnd = new Random(1);
        double[][] w0 = new double[d][d];
        double[][] target = new double[d][d];
        double[] u = rnd.doubles(d).map(v -> v - 0.5).toArray();
        double[] v = rnd.doubles(d).map(x -> x - 0.5).toArray();
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < d; j++) {
                w0[i][j] = rnd.nextGaussian() / Math.sqrt(d);
                target[i][j] = w0[i][j] + 0.5 * u[i] * v[j];
            }
        }
        Lora lora = new Lora(w0, 2, 4, 3);
        double[] x = rnd.doubles(d).toArray();
        assertArrayEquals(Lora.mv(w0, x), lora.forward(x), 1e-12, "B = 0: identical to the frozen model at start");

        double[][] xs = new double[64][];
        double[][] ys = new double[64][];
        for (int n = 0; n < xs.length; n++) {
            xs[n] = rnd.doubles(d).map(z -> z * 2 - 1).toArray();
            ys[n] = Lora.mv(target, xs[n]);
        }
        double first = lora.step(xs, ys, 0.05);
        double last = first;
        for (int epoch = 0; epoch < 400; epoch++) {
            last = lora.step(xs, ys, 0.05);
        }
        assertTrue(last < first * 0.05, "loss " + first + " -> " + last);
        assertEquals(2 * d + d * 2, lora.trainableParameters());
        assertEquals(d * d, lora.fullParameters());

        double[][] merged = lora.merged();
        assertArrayEquals(lora.forward(x), Lora.mv(merged, x), 1e-9, "merging the adapter changes nothing at inference");
    }

    @Test
    void datasetBuilderDeduplicatesFiltersAndSplits() {
        List<DatasetBuilder.Example> raw = List.of(
                new DatasetBuilder.Example("Login fails after the upgrade to 7.4", "{\"category\":\"TECHNICAL\"}"),
                new DatasetBuilder.Example("login FAILS after the upgrade to 7.4!", "{\"category\":\"TECHNICAL\"}"),
                new DatasetBuilder.Example("Refund my invoice INV-1001 please", "{\"category\":\"BILLING\"}"),
                new DatasetBuilder.Example("Contact me at jane.doe@example.com about SSO", "{\"category\":\"ACCOUNT\"}"),
                new DatasetBuilder.Example("hi", "{\"category\":\"OTHER\"}"),
                new DatasetBuilder.Example("Cannot upload documents larger than 10 MB", "{\"category\":\"TECHNICAL\"}"),
                new DatasetBuilder.Example("Change the email address on my account", "{\"category\":\"ACCOUNT\"}"));
        DatasetBuilder.Report r = DatasetBuilder.build("You triage tickets.", raw, 0.25, 1);
        assertEquals(1, r.duplicates());
        assertEquals(2, r.rejected(), "PII and too-short examples are rejected");
        assertEquals(4, r.trainJsonl().size() + r.validationJsonl().size());
        assertEquals(1, r.validationJsonl().size());
        String line = r.trainJsonl().getFirst();
        assertTrue(line.startsWith("{\"messages\":[{\"role\":\"system\""), line);
        assertTrue(r.trainJsonl().stream().noneMatch(l -> l.contains("@example.com")));
    }

    @Test
    void quantisationTradesBytesForError() {
        Random rnd = new Random(2);
        float[] w = new float[4096];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (rnd.nextGaussian() * 0.02);
        }
        w[100] = 1.0f; // outlier
        Quantize.Blocks q8 = Quantize.quantize(w, 32, 8);
        Quantize.Blocks q4 = Quantize.quantize(w, 32, 4);
        Quantize.Blocks q4global = Quantize.quantize(w, w.length, 4);
        double e8 = Quantize.relativeError(w, Quantize.dequantize(q8));
        double e4 = Quantize.relativeError(w, Quantize.dequantize(q4));
        double e4global = Quantize.relativeError(w, Quantize.dequantize(q4global));
        assertTrue(e8 < e4, "more bits, less error");
        assertTrue(e4 < e4global, "small blocks contain the outlier's damage: " + e4 + " vs " + e4global);
        assertTrue(q4.bytes() < q8.bytes() && q8.bytes() < w.length * 4L);
        assertTrue(e8 < 0.05, "int8 is close to lossless here: " + e8);
    }
}
