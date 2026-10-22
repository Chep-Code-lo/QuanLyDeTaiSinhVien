package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.service.AuthService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-http-races;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.springframework.web=INFO"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthHttpRaceIntegrationTests {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired Clock clock;
    @MockitoSpyBean PasswordEncoder encoder;
    @MockitoSpyBean AuthService service;
    @LocalServerPort int port;
    private String email;
    private final AtomicBoolean encodedInTransaction = new AtomicBoolean();

    @BeforeEach
    void fixture() {
        email = "http-race-" + UUID.randomUUID() + "@test.local";
        var now = LocalDateTime.now(clock);
        var user = new User();
        user.setEmail(email); user.setFullName("HTTP race test"); user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(encoder.encode("OldPassword123!")); user.setCreatedAt(now); user.setUpdatedAt(now);
        user.replaceRoles(Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), now);
        users.saveAndFlush(user);
        doAnswer(invocation -> {
            if (path().endsWith("/change-password")) {
                encodedInTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            }
            return invocation.callRealMethod();
        }).when(encoder).encode(anyString());
    }

    @Test
    void independentCookiesKeepCurrentSessionRevokeOthersAndPreserveSessionsAfterFailure() throws Exception {
        var current = client(); var other = client();
        assertThat(login(current).statusCode()).isEqualTo(200);
        assertThat(login(other).statusCode()).isEqualTo(200);
        assertThat(change(current, "incorrect", "NewPassword456!").statusCode()).isEqualTo(400);
        assertThat(get(current, "/me").statusCode()).isEqualTo(200);
        assertThat(get(other, "/me").statusCode()).isEqualTo(200);
        assertThat(change(current, "OldPassword123!", "NewPassword456!").statusCode()).isEqualTo(200);
        assertThat(encodedInTransaction.get()).isTrue();
        assertThat(get(current, "/me").statusCode()).isEqualTo(200);
        assertThat(get(other, "/me").statusCode()).isEqualTo(401);
    }

    @Test
    void loginVerifiedBeforePasswordCommitCannotLeaveAnAuthorizedOldSession() throws Exception {
        var current = client(); var concurrent = client();
        assertThat(login(current).statusCode()).isEqualTo(200);
        var verified = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (path().endsWith("/login")) {
                verified.countDown();
                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(encoder).matches(anyString(), anyString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var login = pool.submit(() -> login(concurrent));
            java.util.concurrent.Future<HttpResponse<String>> changing;
            try {
                assertThat(verified.await(15, TimeUnit.SECONDS)).isTrue();
                var started = new CountDownLatch(1);
                changing = pool.submit(() -> {
                    started.countDown();
                    return change(current, "OldPassword123!", "NewPassword456!");
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                // Login owns the user row until its transaction commits. The
                // password change must wait, rather than deadlocking this test.
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> changing.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            assertThat(login.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(changing.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(get(current, "/me").statusCode()).isEqualTo(200);
        assertThat(get(concurrent, "/me").statusCode()).isEqualTo(401);
    }

    @Test
    void readAlreadyAuthenticatedBeforeCommitMayFinishButItsNextRequestIsUnauthorized() throws Exception {
        var current = client(); var other = client();
        assertThat(login(current).statusCode()).isEqualTo(200);
        assertThat(login(other).statusCode()).isEqualTo(200);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var armed = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (armed.compareAndSet(true, false)) {
                entered.countDown();
                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.callRealMethod();
        }).when(service).currentUser(anyLong());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var reading = pool.submit(() -> get(other, "/me"));
            try {
                assertThat(entered.await(15, TimeUnit.SECONDS)).isTrue();
                assertThat(change(current, "OldPassword123!", "NewPassword456!").statusCode()).isEqualTo(200);
            } finally { release.countDown(); }
            assertThat(reading.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(get(other, "/me").statusCode()).isEqualTo(401);
        assertThat(get(current, "/me").statusCode()).isEqualTo(200);
    }

    private String path() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest().getRequestURI() : "";
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth" + path))
                .timeout(Duration.ofSeconds(25)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(HttpClient client) throws Exception {
        return post(client, "/login", "{\"username\":\"" + email + "\",\"password\":\"OldPassword123!\"}");
    }

    private HttpResponse<String> change(HttpClient client, String oldPassword, String newPassword) throws Exception {
        return post(client, "/change-password", "{\"currentPassword\":\"" + oldPassword
                + "\",\"newPassword\":\"" + newPassword + "\"}");
    }

    private HttpResponse<String> post(HttpClient client, String path, String json) throws Exception {
        assertThat(get(client, "/csrf").statusCode()).isEqualTo(200);
        String csrf = ((CookieManager) client.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth" + path))
                .timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
