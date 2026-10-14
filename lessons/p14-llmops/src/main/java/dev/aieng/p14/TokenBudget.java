package dev.aieng.p14;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting in TOKENS, not requests: one request can cost 50 tokens or 50,000. A token bucket per tenant
 * refills continuously up to its capacity; requests reserve their estimated tokens up front and the actual usage
 * is reconciled afterwards. A separate hard monthly budget stops runaway spend.
 */
public final class TokenBudget {

    public record Decision(boolean allowed, String reason, long remaining) {
    }

    private static final class Bucket {
        double tokens;
        Instant lastRefill;
        long monthlySpent;
    }

    private final long capacity;
    private final double refillPerSecond;
    private final long monthlyLimit;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBudget(long tokensPerMinute, long monthlyLimit, Clock clock) {
        this.capacity = tokensPerMinute;
        this.refillPerSecond = tokensPerMinute / 60.0;
        this.monthlyLimit = monthlyLimit;
        this.clock = clock;
    }

    // region acquire
    public Decision tryAcquire(String tenant, long estimatedTokens) {
        Bucket b = buckets.computeIfAbsent(tenant, t -> {
            Bucket nb = new Bucket();
            nb.tokens = capacity;
            nb.lastRefill = clock.instant();
            return nb;
        });
        synchronized (b) {
            Instant now = clock.instant();
            double elapsed = Duration.between(b.lastRefill, now).toMillis() / 1000.0;
            b.tokens = Math.min(capacity, b.tokens + elapsed * refillPerSecond);
            b.lastRefill = now;
            if (b.monthlySpent + estimatedTokens > monthlyLimit) {
                return new Decision(false, "monthly budget exhausted", 0);
            }
            if (b.tokens < estimatedTokens) {
                long waitSeconds = (long) Math.ceil((estimatedTokens - b.tokens) / refillPerSecond);
                return new Decision(false, "rate limited, retry in " + waitSeconds + "s", (long) b.tokens);
            }
            b.tokens -= estimatedTokens;
            b.monthlySpent += estimatedTokens;
            return new Decision(true, "ok", (long) b.tokens);
        }
    }

    /** After the call, correct the reservation with the real usage reported by the provider. */
    public void reconcile(String tenant, long estimated, long actual) {
        Bucket b = buckets.get(tenant);
        synchronized (b) {
            b.tokens = Math.min(capacity, b.tokens + estimated - actual);
            b.monthlySpent += actual - estimated;
        }
    }
    // endregion
}
