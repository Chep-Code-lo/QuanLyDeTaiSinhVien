package com.example.qldetai.feature.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.communication.domain.*;
import com.example.qldetai.feature.communication.repository.*;
import com.example.qldetai.feature.communication.service.*;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.*;
import com.example.qldetai.security.UserPrincipal;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommunicationPaginationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired FacultyRepository faculties;
    @Autowired AnnouncementRepository announcements;
    @Autowired NotificationService notifications;
    @Autowired AnnouncementService service;
    @Autowired NotificationRepository notificationRepository;
    private User admin, student, lecturer;
    private Faculty faculty, other;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 1, 0);

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        String url = System.getProperty("review.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:")
                    || !url.contains("/ql_de_tai_sinh_vien?"))
                throw new IllegalArgumentException("Disposable local database required");
            properties.add("spring.datasource.url", () -> url);
            properties.add(
                    "spring.datasource.username", () -> System.getProperty("review.mysql.user"));
            properties.add(
                    "spring.datasource.password",
                    () -> System.getProperty("review.mysql.password"));
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
            properties.add("spring.sql.init.mode", () -> "never");
        }
    }

    @BeforeEach
    void fixture() {
        admin = account(RoleCode.ADMIN);
        student = account(RoleCode.STUDENT);
        lecturer = account(RoleCode.LECTURER);
        faculty = faculty();
        other = faculty();
    }

    private User account(RoleCode role) {
        var value = new User();
        value.setEmail(UUID.randomUUID() + "@pagination.local");
        value.setFullName("Người gửi " + role);
        value.setPasswordHash("unused-test-verifier");
        value.setStatus(UserStatus.ACTIVE);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value.replaceRoles(Set.of(roles.findByCode(role).orElseThrow()), now);
        return users.saveAndFlush(value);
    }

    private Faculty faculty() {
        var value = new Faculty();
        value.setCode(UUID.randomUUID().toString().substring(0, 8));
        value.setName("Khoa thử nghiệm");
        value.setActive(true);
        return faculties.saveAndFlush(value);
    }

    private Announcement announcement(String title, AnnouncementStatus status, Faculty scope) {
        var value = new Announcement();
        value.setFaculty(scope);
        value.setCreatedBy(admin);
        value.setTitle(title);
        value.setContent("Nội dung " + title);
        value.setCreatedAt(now);
        value.setStatus(status);
        if (status == AnnouncementStatus.PUBLISHED) value.setPublishedAt(now);
        return announcements.saveAndFlush(value);
    }

    @Test
    void publishedSearchAndPaginationAreStableAndDoNotExposeDrafts() throws Exception {
        var ids = new ArrayList<Long>();
        for (int index = 0; index < 23; index++)
            ids.add(
                    announcement("Tìm kiếm " + index, AnnouncementStatus.PUBLISHED, faculty)
                            .getId());
        announcement("Tìm kiếm nháp", AnnouncementStatus.DRAFT, faculty);
        var first = service.listPublished(faculty.getId(), "TÌM KIẾM", 0, 10);
        var second = service.listPublished(faculty.getId(), "tìm kiếm", 1, 10);
        assertThat(first.totalElements()).isEqualTo(23);
        assertThat(first.content()).hasSize(10);
        assertThat(first.content().getFirst().id()).isEqualTo(ids.getLast());
        assertThat(first.content().stream().map(item -> item.id()))
                .doesNotContainAnyElementsOf(
                        second.content().stream().map(item -> item.id()).toList());
        mvc.perform(
                        get("/api/v1/announcements")
                                .param("facultyId", faculty.getId().toString())
                                .param("search", "kiếm 0")
                                .with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(
                        get("/api/v1/announcements")
                                .param("facultyId", faculty.getId().toString())
                                .with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void searchIsLiteralAndIncludesSenderAndContent() {
        var value = announcement("Tiến độ 100%_A", AnnouncementStatus.PUBLISHED, faculty);
        announcement("Tiến độ 100xxA", AnnouncementStatus.PUBLISHED, faculty);
        assertThat(service.listPublished(faculty.getId(), "%_", 0, 10).content())
                .singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(value.getId()));
        assertThat(
                        service.listPublished(faculty.getId(), admin.getFullName(), 0, 10)
                                .totalElements())
                .isEqualTo(2);
        assertThat(
                        service.listPublished(faculty.getId(), "Nội dung Tiến độ", 0, 10)
                                .totalElements())
                .isEqualTo(2);
    }

    @Test
    void managementSearchKeepsFacultyAndRoleBoundaries() throws Exception {
        announcement("Riêng khoa", AnnouncementStatus.DRAFT, faculty);
        announcement("Riêng khoa", AnnouncementStatus.DRAFT, other);
        mvc.perform(
                        get("/api/v1/announcements/manage")
                                .param("facultyId", faculty.getId().toString())
                                .param("search", "riêng")
                                .param("status", "DRAFT")
                                .with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        for (User outsider : List.of(lecturer, student)) {
            mvc.perform(
                            get("/api/v1/announcements/manage")
                                    .param("facultyId", faculty.getId().toString())
                                    .with(user(UserPrincipal.from(outsider))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void personalSearchIsScopedBeforeCountingAndMarkReadStillWorks() throws Exception {
        for (int index = 0; index < 21; index++)
            notifications.publish(
                    student.getId(),
                    "event-" + index,
                    "Riêng tư " + index,
                    "Tìm nội dung",
                    "/account");
        notifications.publish(
                lecturer.getId(), "private", "Riêng tư giảng viên", "Tìm nội dung", "/account");
        mvc.perform(
                        get("/api/v1/notifications")
                                .param("search", "tìm nội dung")
                                .param("page", "1")
                                .with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(21))
                .andExpect(jsonPath("$.data.content.length()").value(10));
        var item = notifications.list(student.getId(), "Riêng tư 20", 0, 10).content().getFirst();
        mvc.perform(
                        patch("/api/v1/notifications/{id}/read", item.id())
                                .with(user(UserPrincipal.from(lecturer)))
                                .with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        patch("/api/v1/notifications/{id}/read", item.id())
                                .with(user(UserPrincipal.from(student)))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        mvc.perform(
                        get("/api/v1/notifications")
                                .param("size", "101")
                                .with(user(UserPrincipal.from(student))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicReadStateOnlyLoadsTheRequestedPublishedAnnouncementsForThisUser() throws Exception {
        var published = announcement("Công bố", AnnouncementStatus.PUBLISHED, faculty);
        var draft = announcement("Nội bộ", AnnouncementStatus.DRAFT, faculty);
        notifications.publish(
                student.getId(),
                "published",
                published.getTitle(),
                published.getContent(),
                "/announcements");
        notifications.publish(
                student.getId(), "draft", draft.getTitle(), draft.getContent(), "/announcements");
        notifications.publish(
                lecturer.getId(),
                "other",
                published.getTitle(),
                published.getContent(),
                "/announcements");
        mvc.perform(
                        get("/api/v1/notifications/for-announcements")
                                .param("ids", published.getId() + "," + draft.getId())
                                .with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value(published.getTitle()));
    }
}
