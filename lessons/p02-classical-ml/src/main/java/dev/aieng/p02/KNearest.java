package dev.aieng.p02;

import java.util.Arrays;
import java.util.Comparator;

/**
 * k-nearest neighbours: no training at all, just remember the data and vote among the k closest points.
 * It is the conceptual ancestor of vector search and RAG retrieval (Phase 8).
 */
public final class KNearest {

    private final Dataset memory;
    private final int k;

    public KNearest(Dataset memory, int k) {
        this.memory = memory;
        this.k = k;
    }

    // region predict
    public int predict(double[] query) {
        Integer[] order = new Integer[memory.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> squaredDistance(memory.x()[i], query)));
        int votesForOne = 0;
        for (int i = 0; i < Math.min(k, order.length); i++) {
            votesForOne += memory.y()[order[i]] == 1 ? 1 : 0;
        }
        return votesForOne * 2 > Math.min(k, order.length) ? 1 : 0;
    }
    // endregion

    static double squaredDistance(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += (a[i] - b[i]) * (a[i] - b[i]);
        }
        return s;
    }
}
