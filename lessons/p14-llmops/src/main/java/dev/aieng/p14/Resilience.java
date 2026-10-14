package dev.aieng.p14;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Retry with exponential backoff and jitter, and a circuit breaker - the two resilience patterns every LLM call needs. */
public final class Resilience {

    /** Thrown by model clients; status mirrors the provider's HTTP status (429 rate limit, 5xx server errors). */
    public static final class ModelException extends RuntimeException {
        private final int status;

        public ModelException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration d);
    }

    private Resilience() {
    }

    // region retry
    /** Retry only what can succeed on retry: rate limits, server errors and timeouts - never 400s or 401s. */
    public static final Predicate<RuntimeException> RETRYABLE = e ->
            e instanceof ModelException m ? m.status() == 429 || m.status() >= 500 : e instanceof java.util.concurrent.CompletionException;

    /**
     * Exponential backoff with "full jitter": wait a random time in [0, base * 2^attempt], capped. Jitter stops a
     * fleet of clients from retrying in lock-step and hammering a recovering provider (the thundering herd).
     */
    public static <T> T retry(Supplier<T> call, int maxAttempts, Duration base, Duration cap, Random rnd, Sleeper sleeper) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (!RETRYABLE.test(e)) {
                    throw e;
                }
                last = e;
                if (attempt + 1 < maxAttempts) {
                    long ceiling = Math.min(cap.toMillis(), base.toMillis() * (1L << attempt));
                    sleeper.sleep(Duration.ofMillis((long) (rnd.nextDouble() * ceiling)));
                }
            }
        }
        throw last;
    }
    // endregion

    // region breaker
    /**
     * Circuit breaker: after {@code threshold} consecutive failures the circuit OPENS and calls fail fast (no
     * waiting on a dead provider). After {@code cooldown} one trial call is allowed (HALF_OPEN); success closes it.
     */
    public static final class CircuitBreaker {
        public enum State { CLOSED, OPEN, HALF_OPEN }

        private final int threshold;
        private final Duration cooldown;
        private final Clock clock;
        private State state = State.CLOSED;
        private int failures;
        private Instant openedAt;

        public CircuitBreaker(int threshold, Duration cooldown, Clock clock) {
            this.threshold = threshold;
            this.cooldown = cooldown;
            this.clock = clock;
        }

        public synchronized <T> T call(Supplier<T> supplier) {
            if (state == State.OPEN) {
                if (clock.instant().isBefore(openedAt.plus(cooldown))) {
                    throw new IllegalStateException("circuit open: failing fast");
                }
                state = State.HALF_OPEN;
            }
            try {
                T result = supplier.get();
                state = State.CLOSED;
                failures = 0;
                return result;
            } catch (RuntimeException e) {
                failures++;
                if (state == State.HALF_OPEN || failures >= threshold) {
                    state = State.OPEN;
                    openedAt = clock.instant();
                }
                throw e;
            }
        }

        public synchronized State state() {
            return state;
        }
    }
    // endregion
}
