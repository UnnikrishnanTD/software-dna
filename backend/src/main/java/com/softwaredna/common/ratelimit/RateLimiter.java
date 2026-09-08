package com.softwaredna.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A per-key sliding-window rate limiter, kept entirely in memory.
 *
 * <p>This is deliberately not distributed. The service runs as one instance,
 * so a process-local counter is both correct and free of an extra
 * dependency; if the deployment ever scales to multiple instances, this
 * needs to move to a shared store (Redis is the obvious choice) so limits
 * are enforced across all of them rather than per-instance.
 *
 * <p>Windows are fixed rather than a true sliding log, which means a client
 * can burst up to twice the limit across a window boundary. That trade is
 * fine here: the goal is to stop sustained abuse of an endpoint that clones
 * a repository and runs static analysis, not to meter requests exactly.
 *
 * <p>Stale entries are swept on every call so the map cannot grow without
 * bound from a stream of one-off IP addresses.
 */
public class RateLimiter {

    private record Window(Instant startedAt, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration windowSize;

    public RateLimiter(int limit, Duration windowSize) {
        this.limit = limit;
        this.windowSize = windowSize;
    }

    /** True if the request identified by {@code key} is allowed to proceed. */
    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        sweepStale(now);

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || Duration.between(existing.startedAt(), now).compareTo(windowSize) >= 0) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        return window.count().get() <= limit;
    }

    /** Seconds until the caller's window resets, for a Retry-After header. */
    public long secondsUntilReset(String key) {
        Window window = windows.get(key);
        if (window == null) {
            return 0;
        }
        Instant resetAt = window.startedAt().plus(windowSize);
        long remaining = Duration.between(Instant.now(), resetAt).getSeconds();
        return Math.max(remaining, 0);
    }

    private void sweepStale(Instant now) {
        // A cheap amortised cleanup: only worth doing occasionally, since
        // every call already touches the map once via compute().
        if (windows.size() < 10_000) {
            return;
        }
        windows.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().startedAt(), now).compareTo(windowSize) >= 0);
    }
}
