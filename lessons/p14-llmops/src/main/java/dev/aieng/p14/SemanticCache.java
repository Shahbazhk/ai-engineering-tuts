package dev.aieng.p14;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reuse answers for semantically equivalent questions. Three safety rails make it production-worthy: a strict
 * similarity threshold, a SCOPE (tenant + permission set + prompt version) that must match exactly, and a TTL.
 * ponytail: linear scan; use a vector index (Phase 8) when the cache holds more than a few thousand entries.
 */
public final class SemanticCache {

    record Entry(String scope, float[] vector, String question, String answer, Instant expires) {
    }

    public record Hit(String answer, String cachedQuestion, double similarity) {
    }

    private final Function<String, float[]> embed;
    private final double threshold;
    private final Duration ttl;
    private final Clock clock;
    private final List<Entry> entries = new ArrayList<>();

    public SemanticCache(Function<String, float[]> embed, double threshold, Duration ttl, Clock clock) {
        this.embed = embed;
        this.threshold = threshold;
        this.ttl = ttl;
        this.clock = clock;
    }

    // region cache
    public synchronized Optional<Hit> get(String scope, String question) {
        float[] q = normalize(embed.apply(question));
        Instant now = clock.instant();
        entries.removeIf(e -> e.expires().isBefore(now));
        Hit best = null;
        for (Entry e : entries) {
            if (!e.scope().equals(scope)) {
                continue; // never cross tenants, permission sets or prompt versions
            }
            double sim = dot(q, e.vector());
            if (sim >= threshold && (best == null || sim > best.similarity())) {
                best = new Hit(e.answer(), e.question(), sim);
            }
        }
        return Optional.ofNullable(best);
    }

    public synchronized void put(String scope, String question, String answer) {
        entries.add(new Entry(scope, normalize(embed.apply(question)), question, answer, clock.instant().plus(ttl)));
    }
    // endregion

    static float[] normalize(float[] v) {
        double n = 0;
        for (float x : v) {
            n += x * x;
        }
        n = Math.sqrt(n);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = n == 0 ? 0 : (float) (v[i] / n);
        }
        return out;
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }
}
