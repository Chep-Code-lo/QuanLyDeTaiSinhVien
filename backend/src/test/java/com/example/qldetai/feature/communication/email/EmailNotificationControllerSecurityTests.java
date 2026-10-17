package com.example.qldetai.feature.communication.email;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.domain.UserStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class EmailNotificationControllerSecurityTests {

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private User student;
    private User administrator;

    EmailNotificationControllerSecurityTests(
            MockMvc mockMvc,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        student = createUser("email-api-student", RoleCode.STUDENT);
        administrator = createUser("email-api-admin", RoleCode.ADMIN);
    }

    @Test
    void notificationSettingsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/notification-settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessNotificationSettings() throws Exception {
        MockHttpSession session = login(student.getEmail());

        mockMvc.perform(get("/api/v1/notification-settings").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStillRequiresExistingCsrfProtection() throws Exception {
        MockHttpSession session = login(administrator.getEmail());

        mockMvc.perform(put("/api/v1/notification-settings/TASK_ASSIGNED")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scopeType":"SYSTEM",
                                  "scopeId":0,
                                  "enabled":true,
                                  "senderName":"Thông báo hệ thống",
                                  "subjectTemplate":"Nhiệm vụ {{taskName}}",
                                  "bodyTemplate":"<p>{{studentName}}</p>",
                                  "recipientGroups":["DIRECT_PARTICIPANTS"]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"TestPassword123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private User createUser(String prefix, RoleCode roleCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = new User();
        user.setEmail(prefix + "-" + System.nanoTime() + "@test.local");
        user.setFullName(prefix);
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(role), now);
        return userRepository.saveAndFlush(user);
    }
}
