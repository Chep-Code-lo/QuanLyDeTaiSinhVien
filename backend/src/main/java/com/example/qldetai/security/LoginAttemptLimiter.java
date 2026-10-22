package com.example.qldetai.security;

import com.example.qldetai.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {
    private static final int MAX_FAILURES = 5;
    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Clock clock;
    private final Map<Key, Attempt> attempts = new ConcurrentHashMap<>();

    public <T> T authenticate(String username, String ip, Supplier<T> authenticate) {
        Key key = new Key(username.trim().toLowerCase(Locale.ROOT), ip);
        Attempt attempt = attempt(key);
        // Serialize authentication for this pair so simultaneous bad requests cannot bypass the limit.
        synchronized (attempt) {
            Instant now = clock.instant();
            while (!attempt.failures.isEmpty() && !attempt.failures.peekFirst().plus(WINDOW).isAfter(now)) {
                attempt.failures.removeFirst();
            }
            if (attempt.failures.size() >= MAX_FAILURES) {
                throw new TooManyRequestsException("Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.");
            }
            try {
                T result = authenticate.get();
                attempt.failures.clear();
                attempt.expiresAt = clock.instant().plus(WINDOW);
                return result;
            } catch (AuthenticationException exception) {
                Instant failedAt = clock.instant();
                attempt.failures.addLast(failedAt);
                attempt.expiresAt = failedAt.plus(WINDOW);
                throw exception;
            }
        }
    }

    private synchronized Attempt attempt(Key key) {
        Instant now = clock.instant();
        Attempt current = attempts.get(key);
        if (current != null && now.isBefore(current.expiresAt)) return current;
        if (attempts.size() >= MAX_KEYS) {
            attempts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
            if (attempts.size() >= MAX_KEYS) {
                throw new TooManyRequestsException("Có quá nhiều yêu cầu đăng nhập. Vui lòng thử lại sau.");
            }
        }
        Attempt created = new Attempt(now.plus(WINDOW));
        attempts.put(key, created);
        return created;
    }

    private record Key(String username, String ip) {}

    private static final class Attempt {
        private volatile Instant expiresAt;
        private final Deque<Instant> failures = new ArrayDeque<>();

        private Attempt(Instant expiresAt) { this.expiresAt = expiresAt; }
    }
}
