package com.merchtyl.platform.testing;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"dev", "local", "test"})
class TestProvisioningRateLimiter {
    private static final int MAX_ATTEMPTS = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    TestProvisioningRateLimiter() {
        this(Clock.systemUTC());
    }

    TestProvisioningRateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean allow(String key) {
        Instant now = Instant.now(clock);
        AttemptWindow window = attempts.compute(key == null || key.isBlank() ? "unknown" : key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new AttemptWindow(1, now.plus(WINDOW));
            }
            return new AttemptWindow(current.count() + 1, current.expiresAt());
        });
        return window.count() <= MAX_ATTEMPTS;
    }

    private record AttemptWindow(int count, Instant expiresAt) {
    }
}
