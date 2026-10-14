package dev.aieng.p14;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LlmOpsTest {

    /** A clock tests can move forward: time-dependent logic without sleeping. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-14T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void retryBacksOffWithJitterAndOnlyForRetryableErrors() {
        List<Duration> sleeps = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String result = Resilience.retry(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new Resilience.ModelException(429, "rate limited");
            }
            return "ok";
        }, 5, Duration.ofMillis(100), Duration.ofSeconds(2), new Random(1), sleeps::add);
        assertEquals("ok", result);
        assertEquals(2, sleeps.size());
        assertTrue(sleeps.get(0).toMillis() <= 100 && sleeps.get(1).toMillis() <= 200, "full jitter within the exponential ceiling");

        AtomicInteger badCalls = new AtomicInteger();
        assertThrows(Resilience.ModelException.class, () -> Resilience.retry(() -> {
            badCalls.incrementAndGet();
            throw new Resilience.ModelException(400, "bad request");
        }, 5, Duration.ofMillis(100), Duration.ofSeconds(2), new Random(1), d -> { }));
        assertEquals(1, badCalls.get(), "a 400 is never retried");
    }

    @Test
    void circuitBreakerOpensFailsFastAndRecovers() {
        MutableClock clock = new MutableClock();
        Resilience.CircuitBreaker breaker = new Resilience.CircuitBreaker(3, Duration.ofSeconds(30), clock);
        AtomicInteger providerCalls = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            assertThrows(Resilience.ModelException.class, () -> breaker.call(() -> {
                providerCalls.incrementAndGet();
                throw new Resilience.ModelException(503, "down");
            }));
        }
        assertEquals(Resilience.CircuitBreaker.State.OPEN, breaker.state());
        assertThrows(IllegalStateException.class, () -> breaker.call(() -> "x"));
        assertEquals(3, providerCalls.get(), "open circuit: provider not called");

        clock.advance(Duration.ofSeconds(31));
        assertEquals("back", breaker.call(() -> "back"));
        assertEquals(Resilience.CircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void routerSendsHardPromptsToTheStrongModelAndFallsBack() {
        MutableClock clock = new MutableClock();
        var small = new ModelRouter.Model("small", p -> "small:" + p.length(), new Resilience.CircuitBreaker(2, Duration.ofSeconds(30), clock));
        var largeDown = new ModelRouter.Model("large", p -> {
            throw new Resilience.ModelException(503, "overloaded");
        }, new Resilience.CircuitBreaker(2, Duration.ofSeconds(30), clock));
        var backup = new ModelRouter.Model("backup", p -> "backup answer", new Resilience.CircuitBreaker(2, Duration.ofSeconds(30), clock));
        ModelRouter router = new ModelRouter(List.of(
                new ModelRouter.Route("hard", ModelRouter::looksHard, List.of(largeDown, backup)),
                new ModelRouter.Route("default", p -> true, List.of(small, backup))));

        ModelRouter.Result easy = router.handle("Reset my password");
        assertEquals("small", easy.model());
        ModelRouter.Result hard = router.handle("Compare the two contracts step by step");
        assertEquals("backup", hard.model());
        assertEquals(List.of("large:overloaded", "backup:ok"), hard.attempts());
    }

    @Test
    void tokenBucketLimitsByTokensAndEnforcesMonthlyBudget() {
        MutableClock clock = new MutableClock();
        TokenBudget budget = new TokenBudget(6_000, 20_000, clock); // 6k tokens/min = 100/s
        assertTrue(budget.tryAcquire("acme", 5_000).allowed());
        TokenBudget.Decision limited = budget.tryAcquire("acme", 2_000);
        assertFalse(limited.allowed());
        assertTrue(limited.reason().startsWith("rate limited, retry in 10s"), limited.reason());
        assertTrue(budget.tryAcquire("globex", 2_000).allowed(), "tenants have separate buckets");

        clock.advance(Duration.ofSeconds(10));
        assertTrue(budget.tryAcquire("acme", 2_000).allowed());
        budget.reconcile("acme", 2_000, 500); // the call used fewer tokens than estimated

        clock.advance(Duration.ofMinutes(10));
        assertTrue(budget.tryAcquire("acme", 6_000).allowed());
        clock.advance(Duration.ofMinutes(10));
        TokenBudget.Decision monthly = budget.tryAcquire("acme", 6_000);
        assertTrue(monthly.allowed(), "5000 + 500 + 6000 + 6000 = 17,500 <= 20,000");
        clock.advance(Duration.ofMinutes(10));
        assertEquals("monthly budget exhausted", budget.tryAcquire("acme", 6_000).reason());
    }

    @Test
    void semanticCacheHitsOnlyWithinScopeThresholdAndTtl() {
        MutableClock clock = new MutableClock();
        SemanticCache cache = new SemanticCache(LlmOpsTest::bagOfWords, 0.9, Duration.ofHours(1), clock);
        cache.put("acme|employee|v3", "How do I reset my VPN password?", "Use the self-service portal.");

        assertTrue(cache.get("acme|employee|v3", "how do I reset my vpn password").isPresent());
        assertTrue(cache.get("globex|employee|v3", "How do I reset my VPN password?").isEmpty(), "other tenant");
        assertTrue(cache.get("acme|employee|v4", "How do I reset my VPN password?").isEmpty(), "new prompt version");
        assertTrue(cache.get("acme|employee|v3", "How do I order a new laptop?").isEmpty(), "below threshold");
        clock.advance(Duration.ofHours(2));
        assertTrue(cache.get("acme|employee|v3", "How do I reset my VPN password?").isEmpty(), "expired");
    }

    @Test
    void metricsUseGenAiConventionsAndAttributeCostToFeatures() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LlmMetrics metrics = new LlmMetrics(registry);
        metrics.observe("chat", "llama3.2", "tutor", () -> new LlmMetrics.Usage("hi", 1200, 300));
        metrics.observe("chat", "llama3.2", "triage", () -> new LlmMetrics.Usage("ok", 800, 20));
        assertThrows(RuntimeException.class, () -> metrics.observe("chat", "llama3.2", "tutor", () -> {
            throw new Resilience.ModelException(503, "down");
        }));
        assertEquals(2000, metrics.totalTokens("input"), 1e-9);
        assertEquals(320, metrics.totalTokens("output"), 1e-9);
        assertEquals(1, registry.find("gen_ai.client.operation.duration").tag("outcome", "ModelException").timers().size());
        assertEquals(1200, registry.find("gen_ai.client.token.usage").tag("feature", "tutor").tag("gen_ai.token.type", "input")
                .summary().totalAmount(), 1e-9);
    }

    static float[] bagOfWords(String text) {
        float[] v = new float[256];
        for (String w : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!w.isEmpty()) {
                v[Math.floorMod(w.hashCode(), v.length)] += 1;
            }
        }
        return v;
    }
}
