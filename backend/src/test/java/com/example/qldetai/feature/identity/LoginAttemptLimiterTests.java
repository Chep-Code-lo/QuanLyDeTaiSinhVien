package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.qldetai.exception.TooManyRequestsException;
import com.example.qldetai.security.LoginAttemptLimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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
        assertThatThrownBy(
                        () ->
                                limiter.authenticate(
                                        username,
                                        ip,
                                        () -> {
                                            throw new BadCredentialsException("invalid");
                                        }))
                .isExactlyInstanceOf(BadCredentialsException.class);
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
    void distributedFailuresAreLimitedByUsernameAndSuccessResetsAllIps() {
        for (int index = 0; index < 19; index++) fail("distributed", "ip-" + index);
        assertThat(limiter.authenticate("distributed", "good-ip", () -> "ok")).isEqualTo("ok");
        for (int index = 0; index < 20; index++) fail("distributed", "ip-" + index);
        assertThatThrownBy(() -> limiter.authenticate(" DISTRIBUTED ", "new-ip", () -> "blocked"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void randomUsernamesCannotBlockARealUserWhenCacheIsFull() {
        for (int index = 0; index < 15_000; index++) fail("random-" + index, "attacker-ip");
        assertThat(limiter.authenticate("real-user", "real-ip", () -> "ok")).isEqualTo("ok");
        fail("real-user", "real-ip");
    }

    @Test
    void successfulAndInFlightRequestsDoNotAllocateEntries() throws Exception {
        var field = LoginAttemptLimiter.class.getDeclaredField("attempts");
        field.setAccessible(true);
        var cache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(limiter);
        limiter.authenticate(
                "valid",
                "ip",
                () -> {
                    assertThat(cache.estimatedSize()).isZero();
                    return "ok";
                });
        assertThat(cache.estimatedSize()).isZero();
        assertThatThrownBy(
                        () ->
                                limiter.authenticate(
                                        "unresolved",
                                        "ip",
                                        () -> {
                                            throw new IllegalStateException("service unavailable");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    void concurrentDistributedFailuresCannotBypassUsernameLimitOrBlockOtherAccounts()
            throws Exception {
        var calls = new AtomicInteger();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Future<?>>();
            for (int index = 0; index < 64; index++) {
                String ip = "ip-" + index;
                tasks.add(
                        pool.submit(
                                () -> {
                                    gate.await();
                                    try {
                                        limiter.authenticate(
                                                "distributed",
                                                ip,
                                                () -> {
                                                    calls.incrementAndGet();
                                                    throw new BadCredentialsException("invalid");
                                                });
                                    } catch (BadCredentialsException
                                            | TooManyRequestsException expected) {
                                        // Independent IPs share the same account budget.
                                    }
                                    assertThat(
                                                    limiter.authenticate(
                                                            "other-" + ip, "shared-ip", () -> "ok"))
                                            .isEqualTo("ok");
                                    return null;
                                }));
            }
            gate.countDown();
            for (var task : tasks) task.get();
        }
        assertThat(calls.get()).isEqualTo(20);
    }

    @Test
    void concurrentFailuresStillRunOnlyFiveAuthentications() throws Exception {
        var calls = new AtomicInteger();
        var startGate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Future<?>>();
            for (int index = 0; index < 20; index++) {
                tasks.add(
                        pool.submit(
                                () -> {
                                    startGate.await();
                                    try {
                                        limiter.authenticate(
                                                "user",
                                                "ip",
                                                () -> {
                                                    calls.incrementAndGet();
                                                    throw new BadCredentialsException("invalid");
                                                });
                                    } catch (BadCredentialsException
                                            | TooManyRequestsException expected) {
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
