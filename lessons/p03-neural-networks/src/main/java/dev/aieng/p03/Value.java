package dev.aieng.p03;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A scalar that remembers how it was computed, so it can compute its own gradient: reverse-mode automatic
 * differentiation in ~150 lines. Inspired by Andrej Karpathy's micrograd. Every deep-learning framework
 * (PyTorch, DJL, JAX) is this idea applied to tensors instead of scalars.
 */
public final class Value {

    // region fields
    private double data;
    private double grad;
    private final List<Value> parents;
    private final String op;
    private Runnable backward = () -> { };
    // endregion

    public Value(double data) {
        this(data, List.of(), "");
    }

    private Value(double data, List<Value> parents, String op) {
        this.data = data;
        this.parents = parents;
        this.op = op;
    }

    // region ops
    public Value add(Value other) {
        Value out = new Value(data + other.data, List.of(this, other), "+");
        out.backward = () -> {
            // d(a+b)/da = 1, d(a+b)/db = 1: the gradient flows through unchanged.
            this.grad += out.grad;
            other.grad += out.grad;
        };
        return out;
    }

    public Value mul(Value other) {
        Value out = new Value(data * other.data, List.of(this, other), "*");
        out.backward = () -> {
            // d(a*b)/da = b, d(a*b)/db = a: each input's gradient is scaled by the OTHER input.
            this.grad += other.data * out.grad;
            other.grad += this.data * out.grad;
        };
        return out;
    }

    public Value tanh() {
        double t = Math.tanh(data);
        Value out = new Value(t, List.of(this), "tanh");
        out.backward = () -> this.grad += (1 - t * t) * out.grad;
        return out;
    }
    // endregion

    public Value add(double c) {
        return add(new Value(c));
    }

    public Value mul(double c) {
        return mul(new Value(c));
    }

    public Value neg() {
        return mul(-1);
    }

    public Value sub(Value other) {
        return add(other.neg());
    }

    public Value pow(double exponent) {
        Value out = new Value(Math.pow(data, exponent), List.of(this), "^" + exponent);
        out.backward = () -> this.grad += exponent * Math.pow(data, exponent - 1) * out.grad;
        return out;
    }

    public Value div(Value other) {
        return mul(other.pow(-1));
    }

    public Value relu() {
        Value out = new Value(Math.max(0, data), List.of(this), "relu");
        out.backward = () -> this.grad += (out.data > 0 ? 1 : 0) * out.grad;
        return out;
    }

    public Value exp() {
        double e = Math.exp(data);
        Value out = new Value(e, List.of(this), "exp");
        out.backward = () -> this.grad += e * out.grad;
        return out;
    }

    public Value log() {
        Value out = new Value(Math.log(data), List.of(this), "log");
        out.backward = () -> this.grad += (1 / data) * out.grad;
        return out;
    }

    // region backward
    /**
     * Backpropagation: order the graph so every node comes after everything it depends on (topological sort),
     * seed d(this)/d(this) = 1, then walk the order in reverse calling each node's local chain-rule step.
     */
    public void backward() {
        List<Value> order = new ArrayList<>();
        Set<Value> visited = new HashSet<>();
        buildTopo(this, visited, order);
        for (Value v : order) {
            v.grad = 0;
        }
        this.grad = 1;
        for (int i = order.size() - 1; i >= 0; i--) {
            order.get(i).backward.run();
        }
    }

    // Iterative DFS: deep graphs (long training sequences) would overflow the call stack with recursion.
    private static void buildTopo(Value root, Set<Value> visited, List<Value> order) {
        record Frame(Value node, boolean expanded) {
        }
        List<Frame> stack = new ArrayList<>();
        stack.add(new Frame(root, false));
        while (!stack.isEmpty()) {
            Frame f = stack.removeLast();
            if (f.expanded()) {
                order.add(f.node());
                continue;
            }
            if (!visited.add(f.node())) {
                continue;
            }
            stack.add(new Frame(f.node(), true));
            for (Value p : f.node().parents) {
                if (!visited.contains(p)) {
                    stack.add(new Frame(p, false));
                }
            }
        }
    }
    // endregion

    public double data() {
        return data;
    }

    public double grad() {
        return grad;
    }

    /** Gradient-descent update for a parameter; used by the training loop. */
    public void step(double learningRate) {
        data -= learningRate * grad;
    }

    public String op() {
        return op;
    }

    @Override
    public String toString() {
        return "Value(data=" + data + ", grad=" + grad + ")";
    }
}
