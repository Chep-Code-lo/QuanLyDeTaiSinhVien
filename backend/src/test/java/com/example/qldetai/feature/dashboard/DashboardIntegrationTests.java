package com.example.qldetai.feature.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.academic.domain.*;
import com.example.qldetai.feature.communication.domain.Notification;
import com.example.qldetai.feature.groupregistration.domain.*;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.project.domain.*;
import com.example.qldetai.feature.topic.domain.*;
import com.example.qldetai.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTests {
    @Autowired EntityManager em;
    @Autowired DashboardService dashboard;
    @Autowired RoleRepository roles;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    private User actor;
    private Student student;
    private Department department;
    private Semester semester;
    private RegistrationPeriod period;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        actor = new User();
        actor.setEmail("dashboard-" + System.nanoTime() + "@test.local");
        actor.setFullName("Dashboard Viewer");
        actor.setPasswordHash("unused");
        actor.setStatus(UserStatus.ACTIVE);
        actor.setCreatedAt(now);
        actor.setUpdatedAt(now);
        actor.replaceRoles(Set.of(roles.findByCode(RoleCode.STUDENT).orElseThrow()), now);
        em.persist(actor);
        Faculty faculty = new Faculty();
        faculty.setCode("F" + System.nanoTime());
        faculty.setName("Faculty");
        faculty.setActive(true);
        em.persist(faculty);
        department = new Department();
        department.setCode("D" + System.nanoTime());
        department.setName("Department");
        department.setFaculty(faculty);
        department.setActive(true);
        em.persist(department);
        student = new Student();
        student.setUser(actor);
        student.setDepartment(department);
        student.setStudentCode("S" + System.nanoTime());
        student.setClassName("Test");
        student.setCohort("2026");
        em.persist(student);
        semester = new Semester();
        semester.setAcademicYear("TEST-" + System.nanoTime());
        semester.setSemesterNo(1);
        semester.setStartsOn(now.minusMonths(1).toLocalDate());
        semester.setEndsOn(now.plusMonths(4).toLocalDate());
        em.persist(semester);
        period = period(ProjectType.GRADUATION, RegistrationPeriodStatus.ACTIVE, -1, 10);
    }

    @Test
    void zeroTopicsReturnZeroRatherThanDemoNumbers() {
        var response = dashboard.summary(actor.getId());
        assertThat(response.totalTopics()).isZero();
        assertThat(response.registeringTopics()).isZero();
        assertThat(response.inProgressTopics()).isZero();
        assertThat(response.completedTopics()).isZero();
        assertThat(response.topicTypes()).allSatisfy(item -> assertThat(item.count()).isZero());
        assertThat(response.recentTopics()).isEmpty();
    }

    @Test
    void countsFollowDatabaseStatusesAndRegistrationWindows() {
        topic(period, TopicStatus.PUBLISHED);
        topic(period, TopicStatus.PENDING);
        assignment(topic(period, TopicStatus.PUBLISHED), AssignmentStatus.IN_PROGRESS);
        assignment(topic(period, TopicStatus.PUBLISHED), AssignmentStatus.COMPLETED);
        assignment(topic(period, TopicStatus.PUBLISHED), AssignmentStatus.CANCELLED);
        topic(period(ProjectType.COURSE, RegistrationPeriodStatus.CLOSED, -10, -2), TopicStatus.PUBLISHED);
        topic(period(ProjectType.SPECIALIZED, RegistrationPeriodStatus.ACTIVE, 1, 10), TopicStatus.PUBLISHED);
        topic(period(ProjectType.RESEARCH, RegistrationPeriodStatus.DRAFT, -1, 10), TopicStatus.PUBLISHED);
        em.flush();

        var response = dashboard.summary(actor.getId());
        assertThat(response.totalTopics()).isEqualTo(8);
        assertThat(response.registeringTopics()).isEqualTo(1);
        assertThat(response.inProgressTopics()).isEqualTo(1);
        assertThat(response.completedTopics()).isEqualTo(1);
        assertThat(response.openRegistrationPeriods()).isEqualTo(1);
        assertThat(response.topicTypes()).filteredOn(item -> item.projectType() == ProjectType.GRADUATION)
                .singleElement().satisfies(item -> assertThat(item.count()).isEqualTo(5));
        assertThat(response.topicTypes().stream().mapToLong(DashboardResponse.TopicTypeCount::count).sum()).isEqualTo(8);
        assertThat(response.currentSemester().academicYear()).isEqualTo(semester.getAcademicYear());
        assertThat(response.nextRegistrationCloseAt()).isEqualTo(period.getRegistrationEndAt());
        assertThat(response.upcomingDeadlines()).allSatisfy(item -> assertThat(item.dueAt()).isAfter(now));
    }

    @Test
    void recentTopicsAreOrderedByUpdateAndChangesAreVisibleOnRefresh() {
        Topic oldest = topic(period, TopicStatus.PENDING);
        oldest.setUpdatedAt(now.minusDays(3));
        Topic updated = topic(period, TopicStatus.PUBLISHED);
        updated.setUpdatedAt(now.plusSeconds(1));
        Topic latest = topic(period, TopicStatus.PUBLISHED);
        latest.setUpdatedAt(now.plusSeconds(2));
        topic(period, TopicStatus.DRAFT);
        em.flush();

        assertThat(dashboard.summary(actor.getId()).recentTopics()).hasSize(3)
                .extracting(DashboardResponse.TopicPreview::id).startsWith(latest.getId(), updated.getId());
        updated.setStatus(TopicStatus.WITHDRAWN);
        em.flush();
        assertThat(dashboard.summary(actor.getId()).registeringTopics()).isEqualTo(1);
    }

    @Test
    void notificationCountBelongsOnlyToAuthenticatedUser() throws Exception {
        notification(actor, false);
        notification(actor, true);
        var other = new User();
        other.setEmail("other-" + System.nanoTime() + "@test.local");
        other.setFullName("Other"); other.setPasswordHash("unused");
        other.setCreatedAt(now); other.setUpdatedAt(now); other.setStatus(UserStatus.ACTIVE);
        em.persist(other);
        notification(other, false);
        em.flush();

        mvc.perform(get("/api/v1/dashboard").with(user(UserPrincipal.from(actor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTopics").value(0))
                .andExpect(jsonPath("$.data.unreadNotifications").value(1));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    private RegistrationPeriod period(ProjectType type, RegistrationPeriodStatus status, int startsIn, int endsIn) {
        var result = new RegistrationPeriod();
        result.setFaculty(department.getFaculty()); result.setSemester(semester);
        result.setName("Period " + System.nanoTime()); result.setProjectType(type);
        result.setProposalStartAt(now.minusDays(30)); result.setProposalEndAt(now.minusDays(20));
        result.setRegistrationStartAt(now.plusDays(startsIn)); result.setRegistrationEndAt(now.plusDays(endsIn));
        result.setReviewDeadlineAt(type == ProjectType.GRADUATION || type == ProjectType.SPECIALIZED ? now.plusDays(20) : null);
        result.setDefenseDate(type == ProjectType.GRADUATION ? now.plusDays(30).toLocalDate() : null);
        result.setStatus(status); result.setCreatedBy(actor); result.setCreatedAt(now);
        em.persist(result); return result;
    }

    private Topic topic(RegistrationPeriod registrationPeriod, TopicStatus status) {
        var result = new Topic();
        result.setPeriod(registrationPeriod); result.setFaculty(department.getFaculty()); result.setDepartment(department);
        result.setCode("T" + System.nanoTime()); result.setTitle("Actual topic " + System.nanoTime());
        result.setDescription("Description"); result.setObjectives("Objectives"); result.setExpectedOutcomes("Outcomes");
        result.setProposedBy(actor); result.setProposalSource(ProposalSource.STUDENT);
        result.setVisibilityType(VisibilityType.OPEN); result.setStatus(status);
        result.setCreatedAt(now); result.setUpdatedAt(now);
        em.persist(result); return result;
    }

    private void assignment(Topic topic, AssignmentStatus status) {
        var group = new StudentGroup();
        group.setPeriod(period); group.setName("Group " + System.nanoTime()); group.setCreatedBy(student); group.setCreatedAt(now);
        em.persist(group);
        var registration = new TopicRegistration();
        registration.setPeriod(period); registration.setGroup(group); registration.setTopic(topic);
        registration.setRegisteredBy(student); registration.setRegisteredAt(now); registration.setStatus(RegistrationStatus.APPROVED);
        em.persist(registration);
        var assignment = new ProjectAssignment();
        assignment.setRegistration(registration); assignment.setGroup(group); assignment.setTopic(topic); assignment.setPeriod(period);
        assignment.setAssignedBy(actor); assignment.setAssignedAt(now); assignment.setStatus(status);
        em.persist(assignment);
    }

    private void notification(User user, boolean read) {
        var item = new Notification();
        item.setUser(user); item.setTitle("Notice"); item.setContent("Actual notification");
        item.setEventKey("test-" + System.nanoTime()); item.setCreatedAt(now); item.setReadAt(read ? now : null);
        em.persist(item);
    }
}
