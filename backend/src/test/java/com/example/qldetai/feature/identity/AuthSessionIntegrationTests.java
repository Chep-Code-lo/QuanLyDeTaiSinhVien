package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.identity.service.AuthService;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwords;
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
        user.setEmail(email); user.setFullName("Session test"); user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwords.encode("OldPassword123!"));
        var now = LocalDateTime.now(clock);
        user.setCreatedAt(now); user.setUpdatedAt(now);
        user.replaceRoles(Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), now);
        users.saveAndFlush(user);
    }

    private MockHttpSession login() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"OldPassword123!\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void passwordChangeKeepsOnlyTheCurrentSessionAndRejectsTheOldPassword() throws Exception {
        assertThat(AopUtils.isAopProxy(authService)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var current = login();
        var other = login();
        String currentId = current.getId();
        mvc.perform(post("/api/v1/auth/change-password").session(current).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isOk());
        assertThat(passwords.matches("NewPassword456!", users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash())).isTrue();
        assertThat(current.getId()).isEqualTo(currentId);
        mvc.perform(get("/api/v1/auth/me").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(other)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại."));
        assertThat(other.isInvalid()).isTrue();
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"OldPassword123!\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"NewPassword456!\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void failedPasswordChangeDoesNotRevokeEitherSession() throws Exception {
        var current = login();
        var other = login();
        mvc.perform(post("/api/v1/auth/change-password").session(current).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"incorrect\",\"newPassword\":\"NewPassword456!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không chính xác."));
        mvc.perform(get("/api/v1/auth/me").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(other)).andExpect(status().isOk());
    }

    @Test
    void unchangedPasswordIsRejectedInVietnameseWithoutInvalidatingSessions() throws Exception {
        var current = login();
        var other = login();
        mvc.perform(post("/api/v1/auth/change-password").session(current).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"OldPassword123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu mới phải khác mật khẩu hiện tại."));
        mvc.perform(get("/api/v1/auth/me").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(other)).andExpect(status().isOk());
    }
}
