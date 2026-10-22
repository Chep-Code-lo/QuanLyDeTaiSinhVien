package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.service.AuthService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AuthSessionIntegrationTests {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedMysql(org.springframework.test.context.DynamicPropertyRegistry properties) {
        String url = System.getProperty("race.test.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:")
                    || !url.contains("/ql_de_tai_sinh_vien?")) {
                throw new IllegalArgumentException(
                        "Concurrency tests require a disposable local database.");
            }
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add(
                    "spring.datasource.username",
                    () -> System.getProperty("race.test.mysql.user", "root"));
            properties.add(
                    "spring.datasource.password",
                    () -> System.getProperty("race.test.mysql.password"));
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
            properties.add("spring.sql.init.mode", () -> "never");
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private PasswordEncoder passwords;

    @Autowired private Clock clock;
    @Autowired private AuthService authService;
    private String email;

    @AfterEach
    void removeUser() {
        users.findByEmailIgnoreCase(email).ifPresent(users::delete);
    }

    @BeforeEach
    void createUser() {
        email = "session-" + UUID.randomUUID() + "@test.local";
        var user = new User();
        user.setEmail(email);
        user.setFullName("Session test");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwords.encode("OldPassword123!"));
        var now = LocalDateTime.now(clock);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), now);
        users.saveAndFlush(user);
    }

    private MockHttpSession login() throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"username\":\"%s\",\"password\":\"OldPassword123!\"}"
                                                        .formatted(email)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.message").value("Đăng nhập thành công."))
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void passwordChangeKeepsOnlyTheCurrentSessionAndRejectsTheOldPassword() throws Exception {
        assertThat(AopUtils.isAopProxy(authService)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var current = login();
        var other = login();
        String currentId = current.getId();
        mvc.perform(
                        post("/api/v1/auth/change-password")
                                .session(current)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công."));
        assertThat(
                        passwords.matches(
                                "NewPassword456!",
                                users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash()))
                .isTrue();
        assertThat(current.getId()).isEqualTo(currentId);
        mvc.perform(get("/api/v1/auth/me").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(other))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.message")
                                .value("Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại."));
        assertThat(other.isInvalid()).isTrue();
        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"%s\",\"password\":\"OldPassword123!\"}"
                                                .formatted(email)))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"%s\",\"password\":\"NewPassword456!\"}"
                                                .formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void failedPasswordChangeDoesNotRevokeEitherSession() throws Exception {
        var current = login();
        var other = login();
        mvc.perform(
                        post("/api/v1/auth/change-password")
                                .session(current)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"incorrect\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không chính xác."));
        mvc.perform(get("/api/v1/auth/me").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(other)).andExpect(status().isOk());
    }

    @Test
    void unchangedPasswordIsRejectedInVietnameseWithoutInvalidatingSessions() throws Exception {
        var current = login();
        var other = login();
        mvc.perform(
                        post("/api/v1/auth/change-password")
                                .session(current)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"OldPassword123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value("Mật khẩu mới phải khác mật khẩu hiện tại."));
        mvc.perform(get("/api/v1/auth/me").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(other)).andExpect(status().isOk());
    }

    @Test
    void newPasswordLoginWaitsForConcurrentPasswordChangeCommit() throws Exception {
        var changing = login();
        var oldSession = login();
        var encoding = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            encoding.countDown();
                            assertThat(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                                    .isTrue();
                            return invocation.callRealMethod();
                        })
                .when(passwords)
                .encode("NewPassword456!");
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var change =
                    pool.submit(
                            () ->
                                    mvc.perform(
                                                    post("/api/v1/auth/change-password")
                                                            .session(changing)
                                                            .with(csrf())
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    "{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"NewPassword456!\"}"))
                                            .andReturn());
            assertThat(encoding.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var loggingIn = new java.util.concurrent.CountDownLatch(1);
            var freshLogin =
                    pool.submit(
                            () -> {
                                loggingIn.countDown();
                                return mvc.perform(
                                                post("/api/v1/auth/login")
                                                        .with(csrf())
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(
                                                                "{\"username\":\"%s\",\"password\":\"NewPassword456!\"}"
                                                                        .formatted(email)))
                                        .andReturn();
                            });
            try {
                assertThat(loggingIn.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () ->
                                        freshLogin.get(
                                                300, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThat(
                            change.get(15, java.util.concurrent.TimeUnit.SECONDS)
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(200);
            var fresh = freshLogin.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(fresh.getResponse().getStatus()).isEqualTo(200);
            mvc.perform(
                            get("/api/v1/auth/me")
                                    .session(
                                            (MockHttpSession) fresh.getRequest().getSession(false)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/auth/me").session(changing)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/auth/me").session(oldSession))
                    .andExpect(status().isUnauthorized());
        } finally {
            release.countDown();
            org.mockito.Mockito.reset(passwords);
        }
    }

    @Test
    void concurrentPasswordChangesHaveOneWinnerAndRevokeTheLosingSession() throws Exception {
        var first = login();
        var second = login();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var sessions = java.util.List.of(first, second);
            var values = java.util.List.of("FirstPassword456!", "SecondPassword789!");
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                int selected = index;
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    assertThat(
                                                    start.await(
                                                            10,
                                                            java.util.concurrent.TimeUnit.SECONDS))
                                            .isTrue();
                                    return mvc.perform(
                                                    post("/api/v1/auth/change-password")
                                                            .session(sessions.get(selected))
                                                            .with(csrf())
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    "{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"%s\"}"
                                                                            .formatted(
                                                                                    values.get(
                                                                                            selected))))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            }
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var statuses =
                    java.util.List.of(
                            futures.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
                            futures.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(code -> code == 200).count()).isEqualTo(1);
            assertThat(statuses)
                    .allMatch(code -> code == 200 || code == 400 || code == 401 || code == 409);
            int winner = statuses.indexOf(200);
            mvc.perform(get("/api/v1/auth/me").session(sessions.get(winner)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/auth/me").session(sessions.get(1 - winner)))
                    .andExpect(status().isUnauthorized());
            for (int index = 0; index < 2; index++) {
                var result =
                        mvc.perform(
                                        post("/api/v1/auth/login")
                                                .with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"username\":\"%s\",\"password\":\"%s\"}"
                                                                .formatted(
                                                                        email, values.get(index))))
                                .andReturn();
                assertThat(result.getResponse().getStatus()).isEqualTo(index == winner ? 200 : 401);
            }
        } finally {
            start.countDown();
        }
    }

    @Test
    void logoutReturnsVietnameseSuccessAndInvalidatesTheSession() throws Exception {
        var session = login();
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công."));
        assertThat(session.isInvalid()).isTrue();
    }
}
