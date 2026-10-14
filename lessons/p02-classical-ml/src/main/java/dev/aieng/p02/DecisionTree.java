package dev.aieng.p02;

import java.util.ArrayList;
import java.util.List;

/**
 * CART-style binary classification tree. At each node, try every (feature, threshold) split and keep the one
 * that most reduces Gini impurity. Trees need no feature scaling and are easy to explain - a real advantage in
 * regulated domains - but a single deep tree overfits, which is why forests and boosting exist.
 */
public final class DecisionTree {

    sealed interface Node permits Leaf, Split {
    }

    record Leaf(int label, int samples) implements Node {
    }

    record Split(int feature, double threshold, Node left, Node right) implements Node {
    }

    private final int maxDepth;
    private final int minSamples;
    private Node root;

    public DecisionTree(int maxDepth, int minSamples) {
        this.maxDepth = maxDepth;
        this.minSamples = minSamples;
    }

    public DecisionTree fit(Dataset d) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < d.size(); i++) {
            all.add(i);
        }
        root = build(d, all, 0);
        return this;
    }

    // region build
    private Node build(Dataset d, List<Integer> rows, int depth) {
        int ones = countOnes(d, rows);
        int majority = ones * 2 >= rows.size() ? 1 : 0;
        if (depth >= maxDepth || rows.size() < minSamples || ones == 0 || ones == rows.size()) {
            return new Leaf(majority, rows.size());
        }
        double bestScore = gini(ones, rows.size());
        int bestFeature = -1;
        double bestThreshold = 0;
        for (int f = 0; f < d.features(); f++) {
            for (int r : rows) {
                double t = d.x()[r][f];
                int leftN = 0, leftOnes = 0;
                for (int i : rows) {
                    if (d.x()[i][f] <= t) {
                        leftN++;
                        leftOnes += d.y()[i] == 1 ? 1 : 0;
                    }
                }
                int rightN = rows.size() - leftN;
                if (leftN == 0 || rightN == 0) {
                    continue;
                }
                // Weighted impurity of the two children; lower is better.
                double score = (leftN * gini(leftOnes, leftN) + rightN * gini(ones - leftOnes, rightN)) / rows.size();
                if (score < bestScore) {
                    bestScore = score;
                    bestFeature = f;
                    bestThreshold = t;
                }
            }
        }
        if (bestFeature < 0) {
            return new Leaf(majority, rows.size());
        }
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        for (int i : rows) {
            (d.x()[i][bestFeature] <= bestThreshold ? left : right).add(i);
        }
        return new Split(bestFeature, bestThreshold, build(d, left, depth + 1), build(d, right, depth + 1));
    }

    /** Gini impurity 1 - sum p_k^2: 0 for a pure node, 0.5 for a 50/50 binary node. */
    static double gini(int ones, int n) {
        double p = (double) ones / n;
        return 1 - p * p - (1 - p) * (1 - p);
    }
    // endregion

    public int predict(double[] x) {
        Node node = root;
        while (node instanceof Split s) {
            node = x[s.feature()] <= s.threshold() ? s.left() : s.right();
        }
        return ((Leaf) node).label();
    }

    public int depth() {
        return depth(root);
    }

    private static int depth(Node n) {
        return n instanceof Split s ? 1 + Math.max(depth(s.left()), depth(s.right())) : 0;
    }

    private static int countOnes(Dataset d, List<Integer> rows) {
        int c = 0;
        for (int r : rows) {
            c += d.y()[r] == 1 ? 1 : 0;
        }
        return c;
    }
}
