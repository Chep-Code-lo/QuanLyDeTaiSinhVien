package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.*;
import com.example.qldetai.security.UserPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@SpringBootTest(properties = "logging.level.org.springframework.web=INFO")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class NewPasswordApiValidationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    private User admin;
    private String originalHash;

    @BeforeEach
    void fixture() {
        admin = new User();
        admin.setEmail(UUID.randomUUID() + "@password-api.local");
        admin.setFullName("API password verification");
        originalHash = encoder.encode("OldPassword123!");
        admin.setPasswordHash(originalHash);
        admin.setStatus(UserStatus.ACTIVE);
        var now = LocalDateTime.now(java.time.Clock.systemUTC());
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        admin.replaceRoles(Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), now);
        users.saveAndFlush(admin);
    }

    static Stream<Arguments> passwords() {
        return Stream.of(
                Arguments.of("Ab1!", false),
                Arguments.of("abcdefg1!", false),
                Arguments.of("Abcdefgh!", false),
                Arguments.of("Abcdefg1", false),
                Arguments.of("A1!" + "a".repeat(70), false),
                Arguments.of("A1!" + "ề".repeat(23) + "a", false),
                Arguments.of("A1!" + "😀".repeat(17) + "aa", false),
                Arguments.of("A1!" + "a".repeat(97), false),
                Arguments.of("ĐềTài١٢!", true),
                Arguments.of("Abcdef1😀", true),
                Arguments.of("A1!" + "a".repeat(69), true),
                Arguments.of("A1!" + "ề".repeat(23), true),
                Arguments.of("A1!" + "😀".repeat(17) + "a", true));
    }

    @ParameterizedTest(name = "Create password policy case {index}")
    @MethodSource("passwords")
    void directCreateApiEnforcesUnicodePolicy(String password, boolean valid) throws Exception {
        String email = UUID.randomUUID() + "@new-password.local";
        var payload =
                Map.of(
                        "email",
                        email,
                        "fullName",
                        "New account",
                        "roles",
                        List.of("ADMIN"),
                        "password",
                        password);
        mvc.perform(
                        post("/api/v1/admin/users")
                                .with(user(UserPrincipal.from(admin)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(payload)))
                .andExpect(valid ? status().isCreated() : status().isBadRequest());
        assertThat(users.findByEmailIgnoreCase(email).isPresent()).isEqualTo(valid);
        if (valid) {
            String hash = users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
            assertThat(hash.startsWith("{bcrypt}$2a$12$") && encoder.matches(password, hash))
                    .isTrue();
        }
    }

    @ParameterizedTest(name = "Change password policy case {index}")
    @MethodSource("passwords")
    void directChangeApiRejectsInvalidPasswordsWithoutChangingCredentials(
            String password, boolean valid) throws Exception {
        mvc.perform(
                        post("/api/v1/auth/change-password")
                                .with(user(UserPrincipal.from(admin)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "currentPassword",
                                                        "OldPassword123!",
                                                        "newPassword",
                                                        password))))
                .andExpect(valid ? status().isOk() : status().isBadRequest());
        String currentHash = users.findById(admin.getId()).orElseThrow().getPasswordHash();
        assertThat(
                        valid
                                ? encoder.matches(password, currentHash)
                                : originalHash.equals(currentHash))
                .isTrue();
    }
}
