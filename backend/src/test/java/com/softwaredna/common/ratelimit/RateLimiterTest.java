package com.softwaredna.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This limiter is what stands between a public deployment and someone
 * scripting a loop of repository submissions, so its edge cases are tested
 * directly rather than only through the interceptor.
 */
class RateLimiterTest {

    @Test
    void allowsRequestsUpToTheLimit() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isTrue();
    }

    @Test
    void rejectsRequestsBeyondTheLimit() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1));

        limiter.tryAcquire("client-a");
        limiter.tryAcquire("client-a");
        limiter.tryAcquire("client-a");

        assertThat(limiter.tryAcquire("client-a")).isFalse();
        assertThat(limiter.tryAcquire("client-a")).isFalse();
    }

    @Test
    void tracksEachClientIndependently() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isFalse();

        // A second client's budget is untouched by the first's usage.
        assertThat(limiter.tryAcquire("client-b")).isTrue();
    }

    @Test
    void resetsOnceTheWindowElapses() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMillis(100));

        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isFalse();

        Thread.sleep(150);

        assertThat(limiter.tryAcquire("client-a")).isTrue();
    }

    @Test
    void reportsSecondsUntilTheWindowResets() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(30));

        limiter.tryAcquire("client-a");

        long remaining = limiter.secondsUntilReset("client-a");
        assertThat(remaining).isBetween(1L, 30L);
    }

    @Test
    void reportsZeroSecondsForAClientThatHasNeverBeenSeen() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(30));

        assertThat(limiter.secondsUntilReset("never-seen")).isZero();
    }

    @Test
    void toleratesConcurrentAccessFromTheSameKeyWithoutOvercounting() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(50, Duration.ofMinutes(1));
        int threads = 20;
        int attemptsPerThread = 10;
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < attemptsPerThread; j++) {
                    if (limiter.tryAcquire("shared-client")) {
                        allowed.incrementAndGet();
                    }
                }
            });
        }
        for (Thread worker : workers) worker.start();
        for (Thread worker : workers) worker.join();

        // Exactly the configured limit gets through, no matter how many
        // threads raced for it.
        assertThat(allowed.get()).isEqualTo(50);
    }
}
