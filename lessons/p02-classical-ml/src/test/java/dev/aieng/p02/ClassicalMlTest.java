package dev.aieng.p02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClassicalMlTest {

    @Test
    void splitIsReproducibleAndDisjoint() {
        Dataset d = Dataset.linear(100, 1, 1);
        Dataset.Split a = d.split(0.2, 42);
        Dataset.Split b = d.split(0.2, 42);
        assertEquals(80, a.train().size());
        assertEquals(20, a.test().size());
        assertEquals(a.test().y()[0], b.test().y()[0]);
    }

    @Test
    void scalerUsesTrainingStatisticsOnly() {
        Dataset train = new Dataset(new double[][] {{0}, {2}}, new double[] {0, 0});
        Dataset test = new Dataset(new double[][] {{4}}, new double[] {0});
        Dataset.Scaler s = train.fitScaler();
        assertEquals(1.0, s.mean()[0]);
        assertEquals(3.0, s.apply(test).x()[0][0], 1e-12, "(4 - 1) / 1");
    }

    @Test
    void linearRegressionRecoversTheTrueLine() {
        Dataset d = Dataset.linear(500, 0.5, 7);
        LinearRegression m = new LinearRegression().fit(d, 0.01, 5000);
        double[] exact = LinearRegression.closedForm1d(d);
        assertEquals(3, m.weights()[0], 0.05);
        assertEquals(2, m.bias(), 0.2);
        assertEquals(exact[0], m.weights()[0], 0.01, "GD converges to the closed-form solution");
        double[] pred = new double[d.size()];
        for (int i = 0; i < d.size(); i++) {
            pred[i] = m.predict(d.x()[i]);
        }
        assertTrue(Metrics.r2(d.y(), pred) > 0.98);
    }

    @Test
    void logisticRegressionSeparatesBlobs() {
        Dataset.Split s = Dataset.blobs(400, 4, 3).split(0.25, 1);
        LogisticRegression m = new LogisticRegression().fit(s.train(), 0.5, 500, 0);
        int[] pred = new int[s.test().size()];
        double[] scores = new double[s.test().size()];
        for (int i = 0; i < pred.length; i++) {
            pred[i] = m.predict(s.test().x()[i], 0.5);
            scores[i] = m.probability(s.test().x()[i]);
        }
        Metrics.Confusion c = Metrics.Confusion.of(s.test().y(), pred);
        assertTrue(c.accuracy() > 0.95, "accuracy " + c.accuracy());
        assertTrue(Metrics.rocAuc(s.test().y(), scores) > 0.98);
        assertTrue(m.loss(s.train()) < 0.2);
    }

    @Test
    void l2ShrinksWeights() {
        Dataset d = Dataset.blobs(200, 3, 5);
        double free = norm(new LogisticRegression().fit(d, 0.5, 300, 0).weights());
        double penalised = norm(new LogisticRegression().fit(d, 0.5, 300, 0.5).weights());
        assertTrue(penalised < free);
    }

    @Test
    void metricsOnAKnownConfusionMatrix() {
        double[] actual = {1, 1, 1, 1, 0, 0, 0, 0, 0, 0};
        int[] predicted = {1, 1, 1, 0, 1, 0, 0, 0, 0, 0};
        Metrics.Confusion c = Metrics.Confusion.of(actual, predicted);
        assertEquals(new Metrics.Confusion(3, 1, 5, 1), c);
        assertEquals(0.8, c.accuracy(), 1e-12);
        assertEquals(0.75, c.precision(), 1e-12);
        assertEquals(0.75, c.recall(), 1e-12);
        assertEquals(0.75, c.f1(), 1e-12);
        // Positives score .35 and .8, negatives .1 and .4: 3 of the 4 positive/negative pairs are ordered correctly.
        assertEquals(0.75, Metrics.rocAuc(new double[] {0, 0, 1, 1}, new double[] {.1, .4, .35, .8}), 1e-12);
        assertEquals(0.5, Metrics.rocAuc(new double[] {0, 1}, new double[] {.5, .5}), 1e-12, "ties count half");
    }

    @Test
    void ridgeShrinksWeightsAndKFoldScoresAreFinite() {
        Dataset train = Regularization.polynomial(Regularization.noisySine(40, 0.25, 11), 9);
        Dataset scaled = train.fitScaler().apply(train);
        double[] free = Regularization.ridge(scaled, 0, 0.05, 5000);
        double[] shrunk = Regularization.ridge(scaled, 0.1, 0.05, 5000);
        assertTrue(norm(shrunk) < norm(free));
        double cv = Regularization.kFold(scaled, 5,
                (tr, va) -> Regularization.mse(Regularization.ridge(tr, 0.01, 0.05, 2000), va));
        assertTrue(Double.isFinite(cv) && cv > 0);
    }

    @Test
    void underfittingLinearModelLosesToCubicOnASine() {
        Dataset.Split s = Regularization.noisySine(200, 0.1, 2).split(0.3, 1);
        assertTrue(testMse(s, 3) < testMse(s, 1));
    }

    @Test
    void knnAndTreeLearnANonLinearBoundary() {
        Dataset.Split s = Dataset.circle(600, 9).split(0.25, 2);
        KNearest knn = new KNearest(s.train(), 5);
        DecisionTree tree = new DecisionTree(6, 4).fit(s.train());
        LogisticRegression linear = new LogisticRegression().fit(s.train(), 0.5, 500, 0);
        int knnOk = 0, treeOk = 0, linearOk = 0;
        for (int i = 0; i < s.test().size(); i++) {
            double[] x = s.test().x()[i];
            int y = (int) s.test().y()[i];
            knnOk += knn.predict(x) == y ? 1 : 0;
            treeOk += tree.predict(x) == y ? 1 : 0;
            linearOk += linear.predict(x, 0.5) == y ? 1 : 0;
        }
        int n = s.test().size();
        assertTrue(knnOk > 0.9 * n, "knn " + knnOk + "/" + n);
        assertTrue(treeOk > 0.85 * n, "tree " + treeOk + "/" + n);
        assertTrue(linearOk < knnOk, "a straight line cannot draw a circle");
        assertTrue(tree.depth() <= 6);
        assertEquals(0.5, DecisionTree.gini(5, 10), 1e-12);
        assertEquals(0.0, DecisionTree.gini(10, 10), 1e-12);
    }

    private static double testMse(Dataset.Split s, int degree) {
        Dataset train = Regularization.polynomial(s.train(), degree);
        Dataset.Scaler scaler = train.fitScaler();
        double[] w = Regularization.ridge(scaler.apply(train), 0, 0.05, 10_000);
        return Regularization.mse(w, scaler.apply(Regularization.polynomial(s.test(), degree)));
    }

    private static double norm(double[] w) {
        double s = 0;
        for (double v : w) {
            s += v * v;
        }
        return Math.sqrt(s);
    }
}
