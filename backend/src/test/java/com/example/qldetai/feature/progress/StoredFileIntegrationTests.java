package com.example.qldetai.feature.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.example.qldetai.feature.identity.Role;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.RoleRepository;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.identity.UserStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@AutoConfigureMockMvc
@Transactional
class StoredFileIntegrationTests {

    @TempDir
    static Path uploadDirectory;

    @DynamicPropertySource
    static void fileStorage(DynamicPropertyRegistry registry) {
        registry.add("app.files.storage-root", () -> uploadDirectory.toString());
    }

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    StoredFileIntegrationTests(
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
    void createAdmin() {
        if (userRepository.findByEmailIgnoreCase("admin@test.local").isPresent()) {
            return;
        }
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
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
    void uploadStoresMetadataAndAllowsFileLookup() throws Exception {
        MockHttpSession session = login("admin@test.local", "TestPassword123!");
        byte[] content = "hello backend".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\report-final.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                content);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalName").value("report-final.pdf"))
                .andExpect(jsonPath("$.data.mimeType").value(MediaType.APPLICATION_PDF_VALUE))
                .andExpect(jsonPath("$.data.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn();

        String responseBody = upload.getResponse().getContentAsString();
        long storedFileId = extractLong(responseBody, "\"id\"\\s*:\\s*(\\d+)");
        assertThat(storedFileId).isPositive();
        assertThat(extractText(responseBody, "\"storageKey\"\\s*:\\s*\"([^\"]+)\""))
                .endsWith("-report-final.pdf");
        assertThat(extractText(responseBody, "\"sha256\"\\s*:\\s*\"([^\"]+)\""))
                .hasSize(64);

        mockMvc.perform(get("/api/v1/files/{id}", storedFileId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(storedFileId))
                .andExpect(jsonPath("$.data.originalName").value("report-final.pdf"))
                .andExpect(jsonPath("$.data.mimeType").value(MediaType.APPLICATION_PDF_VALUE))
                .andExpect(jsonPath("$.data.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.data.status").value("READY"));

        mockMvc.perform(get("/api/v1/files/{id}/content", storedFileId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void unrelatedUserCannotReadAnotherUsersUnsubmittedFile() throws Exception {
        createStudentUser("owner@test.local");
        createStudentUser("other@test.local");
        MockHttpSession owner = login("owner@test.local", "TestPassword123!");
        MockHttpSession other = login("other@test.local", "TestPassword123!");
        MvcResult upload = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "report.txt", "text/plain", new byte[] {1, 2, 3}))
                        .session(owner).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        long id = extractLong(upload.getResponse().getContentAsString(), "\"id\"\\s*:\\s*(\\d+)");

        mockMvc.perform(get("/api/v1/files/{id}", id).session(owner)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/files/{id}", id).session(other)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/files/{id}/content", id).session(other))
                .andExpect(status().isForbidden());
    }

    @Test
    void filesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/files/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/files/1/content")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "hello.txt", "text/plain", new byte[] {1}))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingAndEmptyFilesProduceBadRequest() throws Exception {
        MockHttpSession session = login("admin@test.local", "TestPassword123!");
        mockMvc.perform(multipart("/api/v1/files").session(session).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                        .session(session).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFileMetadataReturnsNotFound() throws Exception {
        MockHttpSession session = login("admin@test.local", "TestPassword123!");
        mockMvc.perform(get("/api/v1/files/999999").session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/999999/content").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void missingFileContentReturnsNotFound() throws Exception {
        MockHttpSession session = login("admin@test.local", "TestPassword123!");
        MvcResult upload = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "missing.txt", "text/plain", new byte[] {1}))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        String body = upload.getResponse().getContentAsString();
        long id = extractLong(body, "\"id\"\\s*:\\s*(\\d+)");
        String key = extractText(body, "\"storageKey\"\\s*:\\s*\"([^\"]+)\"");
        Files.delete(uploadDirectory.resolve(key));

        mockMvc.perform(get("/api/v1/files/{id}/content", id).session(session))
                .andExpect(status().isNotFound());
    }

    private void createStudentUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setFullName("File test student");
        user.setStatus(UserStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now(clock);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(roleRepository.findByCode(RoleCode.STUDENT).orElseThrow()), now);
        userRepository.saveAndFlush(user);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private long extractLong(String text, String regex) {
        return Long.parseLong(extractText(text, regex));
    }

    private String extractText(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
