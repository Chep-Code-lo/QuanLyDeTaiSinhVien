package com.example.qldetai.security;

import com.example.qldetai.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Supplier;

@Component
public class LoginAttemptLimiter {
    private static final int MAX_PAIR_FAILURES = 5;
    private static final int MAX_USERNAME_FAILURES = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Clock clock;
    // Counters live in this instance only; multiple backend instances need a shared store.
    private final Cache<String, Deque<Failure>> attempts;
    private final Object[] locks = new Object[256];

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
        attempts =
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(WINDOW)
                        .ticker(() -> Duration.between(Instant.EPOCH, clock.instant()).toNanos())
                        .build();
        for (int index = 0; index < locks.length; index++) locks[index] = new Object();
    }

    public <T> T authenticate(String username, String ip, Supplier<T> authenticate) {
        String key = username.trim().toLowerCase(Locale.ROOT);
        // Fixed locks serialize all IPs for a username without allocating cache entries
        // for successful or in-flight authentication. Eviction never causes a global 429.
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            Deque<Failure> failures = attempts.getIfPresent(key);
            Instant now = clock.instant();
            if (failures != null) {
                while (!failures.isEmpty()
                        && !failures.peekFirst().at().plus(WINDOW).isAfter(now)) {
                    failures.removeFirst();
                }
                if (failures.size() >= MAX_USERNAME_FAILURES
                        || failures.stream()
                                        .filter(
                                                failure ->
                                                        java.util.Objects.equals(failure.ip(), ip))
                                        .count()
                                >= MAX_PAIR_FAILURES) {
                    throw new TooManyRequestsException(
                            "Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.");
                }
            }
            try {
                T result = authenticate.get();
                attempts.invalidate(key);
                return result;
            } catch (AuthenticationException exception) {
                if (failures == null) failures = new ArrayDeque<>();
                failures.addLast(new Failure(clock.instant(), ip));
                attempts.put(key, failures);
                throw exception;
            }
        }
    }

    private record Failure(Instant at, String ip) {}
}
