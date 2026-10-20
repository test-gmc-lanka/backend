package com.gmc.backend.security;

import com.gmc.backend.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory sliding-window limiter for abuse-prone endpoints (password guessing,
 * reset-email flooding). Fine for a single server; use Redis/Bucket4j when scaling out.
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(String key, int maxRequests, Duration window, String message) {
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
                times.pollFirst();
            }
            if (times.size() >= maxRequests) {
                throw new TooManyRequestsException(message);
            }
            times.addLast(now);
        }
        if (hits.size() > CLEANUP_THRESHOLD) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || now - e.getValue().peekLast() > windowMs;
                }
            });
        }
    }
}
