package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.qldetai.exception.TooManyRequestsException;
import com.example.qldetai.security.LoginAttemptLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class LoginAttemptLimiterTests {
    private final Instant start = Instant.parse("2026-10-22T00:00:00Z");
    private Clock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setup() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(start);
        limiter = new LoginAttemptLimiter(clock);
    }

    private void fail(String username, String ip) {
        assertThatThrownBy(() -> limiter.authenticate(username, ip, () -> {
            throw new BadCredentialsException("invalid");
        })).isExactlyInstanceOf(BadCredentialsException.class);
    }

    @Test
    void sixthAttemptIsBlockedUntilTheFifteenMinuteWindowExpires() {
        for (int index = 0; index < 5; index++) fail("user", "ip");
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> limiter.authenticate(" USER ", "ip", calls::incrementAndGet))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(calls.get()).isZero();
        when(clock.instant()).thenReturn(start.plus(15, ChronoUnit.MINUTES));
        fail("user", "ip");
    }

    @Test
    void windowBoundaryOnlyExpiresOldFailuresInsteadOfAllowingAFreshBurst() {
        fail("user", "ip");
        when(clock.instant()).thenReturn(start.plus(14, ChronoUnit.MINUTES));
        for (int index = 0; index < 4; index++) fail("user", "ip");
        when(clock.instant()).thenReturn(start.plus(15, ChronoUnit.MINUTES));
        fail("user", "ip");
        assertThatThrownBy(() -> limiter.authenticate("user", "ip", () -> "blocked"))
                .isInstanceOf(TooManyRequestsException.class);
        when(clock.instant()).thenReturn(start.plus(29, ChronoUnit.MINUTES));
        fail("user", "ip");
    }

    @Test
    void successfulAuthenticationResetsTheCounterAndPairsAreIndependent() {
        for (int index = 0; index < 4; index++) fail("user", "ip");
        assertThat(limiter.authenticate("user", "ip", () -> "ok")).isEqualTo("ok");
        for (int index = 0; index < 5; index++) fail("user", "ip");
        fail("other", "ip");
        fail("user", "other-ip");
    }

    @Test
    void concurrentFailuresStillRunOnlyFiveAuthentications() throws Exception {
        var calls = new AtomicInteger();
        var startGate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Future<?>>();
            for (int index = 0; index < 20; index++) {
                tasks.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        limiter.authenticate("user", "ip", () -> {
                            calls.incrementAndGet();
                            throw new BadCredentialsException("invalid");
                        });
                    } catch (BadCredentialsException | TooManyRequestsException expected) {
                        // Both outcomes must count against the same pair.
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (var task : tasks) task.get();
        }
        assertThat(calls.get()).isEqualTo(5);
    }
}
