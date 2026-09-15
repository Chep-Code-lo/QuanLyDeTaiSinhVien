package com.example.qldetai.feature.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createAdmin() {
        if (userRepository.findByEmailIgnoreCase("admin@test.local").isPresent()) {
            return;
        }
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setEmail("admin@test.local");
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setFullName("Test Admin");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(adminRole), now);
        userRepository.saveAndFlush(user);
    }

    @Test
    void loginCreatesSessionAndAllowsAdminAccess() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.username").value("admin@test.local"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Test Admin"));
        mockMvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void revokedAdminRoleTakesEffectForExistingSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        User user = userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        Role studentRole = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();
        user.replaceRoles(Set.of(studentRole), LocalDateTime.now());
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isForbidden());
    }
}
