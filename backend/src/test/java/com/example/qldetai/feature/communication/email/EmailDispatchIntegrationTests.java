package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.*;
import com.example.qldetai.feature.groupregistration.domain.*;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.topic.domain.*;
import com.example.qldetai.feature.communication.email.EmailTemplateDtos.SaveEmailTemplateRequest;
import jakarta.persistence.EntityManager;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.SendEmailRequest;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.security.UserPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email-dispatch;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailDispatchIntegrationTests {
    @Autowired EmailDispatchService dispatch;
    @Autowired EmailNotificationSettingRepository settings;
    @Autowired EmailOutboxRepository outbox;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired EmailTemplateService templates;
    @Autowired EmailLogService logs;
    private User admin;
    private User student;
    private User lecturer;
    private User disabled;

    @DynamicPropertySource
    static void isolatedMysql(DynamicPropertyRegistry properties) {
        String url = System.getProperty("email.test.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/ql_de_tai_sinh_vien?")) {
                throw new IllegalArgumentException("Email tests require an explicitly selected disposable local database.");
            }
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add("spring.datasource.username", () -> "root");
            properties.add("spring.datasource.password", () -> "isolated-email-test");
            // Exercise the SQL schema as deployed (several legacy bounded integers use TINYINT).
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        }
    }

    @BeforeEach
    void fixture() {
        admin = account("Administrator", Set.of(RoleCode.ADMIN));
        student = account("Student recipient", Set.of(RoleCode.STUDENT));
        lecturer = account("Lecturer recipient", Set.of(RoleCode.LECTURER));
        disabled = account("Disabled recipient", Set.of(RoleCode.STUDENT));
        disabled.setStatus(UserStatus.DISABLED);
        EmailNotificationSetting setting = new EmailNotificationSetting();
        setting.setEventType(EmailNotificationEventType.TASK_ASSIGNED);
        setting.setScopeType(NotificationScopeType.SYSTEM); setting.setScopeId(0L);
        setting.setSenderName("University"); setting.setFromEmail("thongbao@test.local");
        setting.setSubjectTemplate("Announcement"); setting.setBodyTemplate("Hello {{studentName}}");
        setting.setRecipientGroups("DIRECT_PARTICIPANTS"); setting.setUpdatedBy(admin);
        // Automatic events may be off; explicit manual dispatch still works.
        setting.setEnabled(false); settings.saveAndFlush(setting);
    }

    @Test
    void studentGroupDispatchDoesNotAddLoggedInAdministrator() {
        var result = dispatch.send(admin.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of()));
        assertThat(result.recipientCount()).isEqualTo(1);
        assertThat(outbox.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getRecipientEmail()).isEqualTo(student.getEmail());
            assertThat(message.getRecipientEmail()).isNotEqualTo(admin.getEmail());
            assertThat(message.getBody()).isEqualTo("Hello Student recipient");
            assertThat(message.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
            assertThat(message.getEventKey()).startsWith("email-manual:");
        });
    }

    @Test
    void lecturerGroupDispatchSelectsOnlyLecturers() {
        assertThat(dispatch.send(admin.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(RoleCode.LECTURER), Set.of())).recipientCount()).isEqualTo(1);
        assertThat(outbox.findAll()).extracting(EmailOutbox::getRecipientEmail).containsExactly(lecturer.getEmail());
    }

    @Test
    void specificDispatchUsesOnlyExplicitlySelectedPeople() {
        dispatch.send(admin.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(), Set.of(lecturer.getId())));
        assertThat(outbox.findAll()).extracting(EmailOutbox::getRecipientEmail).containsExactly(lecturer.getEmail());
    }

    @Test
    void overlappingGroupsAndSpecificSelectionDoNotDuplicateEmail() {
        student.replaceRoles(Set.copyOf(roles.findAllByCodeIn(Set.of(RoleCode.STUDENT, RoleCode.LECTURER))), LocalDateTime.now(clock));
        users.flush();
        assertThat(dispatch.send(admin.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(RoleCode.STUDENT, RoleCode.LECTURER), Set.of(student.getId()))).recipientCount())
                .isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(2);
    }

    @Test
    void updatedRecipientRolesPersistWithoutDuplicateLinks() {
        var bothRoles = Set.copyOf(roles.findAllByCodeIn(Set.of(RoleCode.STUDENT, RoleCode.LECTURER)));
        student.replaceRoles(bothRoles, LocalDateTime.now(clock)); users.flush();
        student.replaceRoles(bothRoles, LocalDateTime.now(clock)); users.flush();
        assertThat(dispatch.recipients(admin.getId(), RoleCode.STUDENT, student.getEmail(), 0, 20).totalElements()).isEqualTo(1);
        assertThat(dispatch.recipients(admin.getId(), RoleCode.LECTURER, student.getEmail(), 0, 20).totalElements()).isEqualTo(1);
        student.replaceRoles(Set.copyOf(roles.findAllByCodeIn(Set.of(RoleCode.LECTURER))), LocalDateTime.now(clock));
        users.flush(); em.clear();
        assertThat(dispatch.recipients(admin.getId(), RoleCode.STUDENT, student.getEmail(), 0, 20).totalElements()).isZero();
        assertThat(dispatch.recipients(admin.getId(), RoleCode.LECTURER, student.getEmail(), 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void groupDispatchProcessesMoreThanOnePage() {
        for (int index = 0; index < 101; index++) account("Student " + index, Set.of(RoleCode.STUDENT));
        assertThat(dispatch.send(admin.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of())).recipientCount()).isEqualTo(102);
        assertThat(outbox.count()).isEqualTo(102);
    }

    @Test
    void missingOrUnavailableSelectionCannotFallBackToCurrentAccount() {
        assertThatThrownBy(() -> dispatch.send(admin.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(), Set.of()))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> dispatch.send(admin.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(), Set.of(student.getId(), disabled.getId())))).isInstanceOf(BusinessRuleException.class);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void searchAndRoleFiltersArePaginatedAndExcludeUnavailableAccounts() {
        var matches = dispatch.recipients(admin.getId(), RoleCode.STUDENT, "Student", 0, 1);
        assertThat(matches.totalElements()).isEqualTo(1);
        assertThat(matches.content()).singleElement().satisfies(person -> assertThat(person.userId()).isEqualTo(student.getId()));
        assertThat(dispatch.recipients(admin.getId(), null, lecturer.getEmail(), 0, 20).content())
                .singleElement().satisfies(person -> assertThat(person.userId()).isEqualTo(lecturer.getId()));
    }

    @Test
    void serviceRejectsStudentsAndLecturersWithoutProfiles() {
        assertThatThrownBy(() -> dispatch.recipients(student.getId(), null, "", 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dispatch.send(lecturer.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of()))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void newEndpointsKeepAuthenticationAuthorizationAndCsrfProtection() throws Exception {
        mvc.perform(get("/api/v1/email-recipients")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/email-recipients").with(user(UserPrincipal.from(student)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/notification-settings/TASK_ASSIGNED/send")
                .with(user(UserPrincipal.from(admin))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientRoles\":[\"STUDENT\"],\"recipientUserIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendEndpointReturnsCountAndDoesNotQueueForCurrentUser() throws Exception {
        mvc.perform(post("/api/v1/notification-settings/TASK_ASSIGNED/send")
                .with(user(UserPrincipal.from(admin))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientRoles\":[],\"recipientUserIds\":[" + student.getId() + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recipientCount").value(1));
        assertThat(outbox.findAll()).extracting(EmailOutbox::getRecipientEmail).containsExactly(student.getEmail());
    }

    @Test
    void lecturerSearchAndGroupDispatchOnlyIncludeSupervisedStudents() {
        var scope = teachingScope();
        var template = templates.create(lecturer.getId(), templateRequest(null));
        assertThat(dispatch.recipients(lecturer.getId(), RoleCode.STUDENT, "", 0, 20).content())
                .extracting(EmailDispatchDtos.EmailRecipientResponse::userId).containsExactly(student.getId());
        assertThat(dispatch.recipients(lecturer.getId(), null, scope.colleague().getEmail(), 0, 20).content()).isEmpty();
        assertThat(dispatch.recipients(lecturer.getId(), null, scope.outsider().getEmail(), 0, 20).totalElements()).isZero();
        assertThat(dispatch.sendTemplate(lecturer.getId(), template.id(),
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of())).recipientCount()).isEqualTo(1);
        assertThat(outbox.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getRecipientEmail()).isEqualTo(student.getEmail());
            assertThat(message.getResolvedScopeType()).isEqualTo(NotificationScopeType.LECTURER);
            assertThat(message.getResolvedScopeId()).isEqualTo(lecturer.getId());
        });
    }

    @Test
    void lecturerCannotTargetColleaguesByRoleOrExplicitId() {
        var scope = teachingScope();
        var template = templates.create(lecturer.getId(), templateRequest(null));
        assertThatThrownBy(() -> dispatch.recipients(lecturer.getId(), RoleCode.LECTURER, "", 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dispatch.sendTemplate(lecturer.getId(), template.id(),
                new SendEmailRequest(Set.of(RoleCode.LECTURER), Set.of()))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dispatch.sendTemplate(lecturer.getId(), template.id(),
                new SendEmailRequest(Set.of(), Set.of(scope.colleague().getId())))).isInstanceOf(BusinessRuleException.class);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void resultEmailsCannotBeSentToArbitraryManualRecipients() {
        assertThatThrownBy(() -> dispatch.send(admin.getId(), EmailNotificationEventType.RESULT_PUBLISHED,
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of()))).isInstanceOf(BusinessRuleException.class);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void explicitIdsCannotBypassLecturerRecipientScopeOrQueuePartialSelection() {
        var scope = teachingScope();
        var template = templates.create(lecturer.getId(), templateRequest(null));
        assertThatThrownBy(() -> dispatch.sendTemplate(lecturer.getId(), template.id(),
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of(student.getId(), scope.outsider().getId()))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void pendingRejectedWithdrawnRegistrationsAndFormerMembersAreExcluded() {
        var scope = teachingScope();
        for (RegistrationStatus status : new RegistrationStatus[] {
                RegistrationStatus.PENDING, RegistrationStatus.REJECTED, RegistrationStatus.WITHDRAWN }) {
            scope.registration().setStatus(status); em.flush();
            assertThat(dispatch.recipients(lecturer.getId(), RoleCode.STUDENT, "", 0, 20).totalElements()).isZero();
        }
        scope.registration().setStatus(RegistrationStatus.APPROVED);
        scope.member().setLeftAt(LocalDateTime.now(clock)); em.flush();
        assertThat(dispatch.recipients(lecturer.getId(), RoleCode.STUDENT, "", 0, 20).totalElements()).isZero();
    }

    @Test
    void lecturerDefaultDispatchRequiresTheirOwnSavedSetting() {
        teachingScope();
        assertThatThrownBy(() -> dispatch.send(lecturer.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of()))).isInstanceOf(BusinessRuleException.class);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void lecturerDefaultDispatchUsesPersonalContentAndScope() {
        teachingScope();
        EmailNotificationSetting own = new EmailNotificationSetting();
        own.setEventType(EmailNotificationEventType.TASK_ASSIGNED); own.setScopeType(NotificationScopeType.LECTURER);
        own.setScopeId(lecturer.getId()); own.setSenderName("Lecturer"); own.setFromEmail("thongbao@test.local");
        own.setSubjectTemplate("Personal announcement"); own.setBodyTemplate("Hello {{studentName}}");
        own.setRecipientGroups("DIRECT_PARTICIPANTS"); own.setUpdatedBy(lecturer); settings.saveAndFlush(own);
        dispatch.send(lecturer.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of()));
        assertThat(outbox.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getSubject()).isEqualTo("Personal announcement");
            assertThat(message.getResolvedScopeId()).isEqualTo(lecturer.getId());
        });
    }

    @Test
    void customTemplatesPersistAndUpdateWithoutReplacingDefaults() {
        var created = templates.create(admin.getId(), templateRequest(null));
        em.clear();
        assertThat(templates.list(admin.getId(), 0, 1).content()).singleElement().satisfies(template -> {
            assertThat(template.id()).isEqualTo(created.id());
            assertThat(template.bodyTemplate()).isEqualTo("Hello {{studentName}}, meeting at 8.");
        });
        var updated = templates.update(created.id(), admin.getId(), templateRequest(created.version()));
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(settings.count()).isEqualTo(1);
        dispatch.sendTemplate(admin.getId(), created.id(), new SendEmailRequest(Set.of(), Set.of(student.getId())));
        assertThat(outbox.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getSubject()).isEqualTo("Meeting invitation");
            assertThat(message.getBody()).isEqualTo("Hello Student recipient, meeting at 8.");
        });
    }

    @Test
    void templatesArePrivateToTheirAuthorAndListsArePaginated() {
        teachingScope();
        var created = templates.create(admin.getId(), templateRequest(null));
        templates.create(admin.getId(), templateRequest(null));
        assertThat(templates.list(admin.getId(), 0, 1).totalElements()).isEqualTo(2);
        assertThat(templates.list(admin.getId(), 1, 1).content()).hasSize(1);
        assertThat(templates.list(lecturer.getId(), 0, 20).content()).isEmpty();
        assertThatThrownBy(() -> templates.update(created.id(), lecturer.getId(), templateRequest(0)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> dispatch.sendTemplate(lecturer.getId(), created.id(),
                new SendEmailRequest(Set.of(RoleCode.STUDENT), Set.of()))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void staleTemplateUpdatesCannotOverwriteNewerContent() {
        var created = templates.create(admin.getId(), templateRequest(null));
        var changed = new SaveEmailTemplateRequest("Updated", "University", "thongbao@test.local", null,
                "Updated subject", "Updated body", created.version());
        var updated = templates.update(created.id(), admin.getId(), changed);
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThatThrownBy(() -> templates.update(created.id(), admin.getId(), templateRequest(created.version())))
                .isInstanceOf(ConflictException.class);
        assertThat(templates.list(admin.getId(), 0, 20).content().getFirst().subjectTemplate()).isEqualTo("Updated subject");
    }

    @Test
    void deanCanSeeTheirOwnManualEmailsInListAndDetail() {
        var scope = teachingScope();
        AcademicAppointment appointment = new AcademicAppointment(); appointment.setLecturer(scope.profile());
        appointment.setFaculty(scope.profile().getDepartment().getFaculty()); appointment.setPositionCode(PositionCode.DEAN);
        appointment.setStartsAt(LocalDateTime.now(clock).minusDays(1)); appointment.setAssignedBy(admin); em.persist(appointment);
        var template = templates.create(lecturer.getId(), templateRequest(null));
        dispatch.sendTemplate(lecturer.getId(), template.id(), new SendEmailRequest(Set.of(), Set.of(student.getId())));
        var ownLogs = logs.list(lecturer.getId(), 0, 20);
        assertThat(ownLogs.totalElements()).isEqualTo(1);
        assertThat(logs.get(lecturer.getId(), outbox.findAll().getFirst().getId())).isNotNull();
    }

    @Test
    void customTemplateEndpointsValidateInputsAndKeepAuthorizationAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/email-templates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/email-templates").with(user(UserPrincipal.from(student)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/email-templates").with(user(UserPrincipal.from(admin)))
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/email-templates").with(user(UserPrincipal.from(admin))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/email-templates").with(user(UserPrincipal.from(admin))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Meeting","senderName":"University","fromEmail":"spoof@invalid.local",
                         "subjectTemplate":"Meeting","bodyTemplate":"Join us"}
                        """)) .andExpect(status().isBadRequest());
    }

    private SaveEmailTemplateRequest templateRequest(Integer version) {
        return new SaveEmailTemplateRequest("Meeting", "University", "thongbao@test.local", null,
                "Meeting invitation", "Hello {{studentName}}, meeting at 8.", version);
    }

    private TeachingScope teachingScope() {
        Department department = department();
        Lecturer profile = lecturerProfile(lecturer, department);
        User colleague = account("Faculty colleague", Set.of(RoleCode.LECTURER)); lecturerProfile(colleague, department);
        User outsider = account("Other faculty lecturer", Set.of(RoleCode.LECTURER)); lecturerProfile(outsider, department());
        account("Unrelated student", Set.of(RoleCode.STUDENT));
        Student supervised = new Student(); supervised.setUser(student); supervised.setDepartment(department);
        supervised.setStudentCode(UUID.randomUUID().toString().substring(0, 20)); supervised.setClassName("A");
        supervised.setCohort("2026"); em.persist(supervised);
        Semester semester = new Semester(); semester.setAcademicYear("2026-2027"); semester.setSemesterNo(1);
        semester.setStartsOn(LocalDateTime.now(clock).toLocalDate().minusMonths(1));
        semester.setEndsOn(LocalDateTime.now(clock).toLocalDate().plusMonths(3)); em.persist(semester);
        RegistrationPeriod period = new RegistrationPeriod(); period.setFaculty(department.getFaculty()); period.setSemester(semester);
        period.setName("Email scope"); period.setProjectType(ProjectType.COURSE); period.setCreatedBy(admin);
        period.setProposalStartAt(LocalDateTime.now(clock).minusDays(10)); period.setProposalEndAt(LocalDateTime.now(clock).minusDays(5));
        period.setRegistrationStartAt(LocalDateTime.now(clock).minusDays(4)); period.setRegistrationEndAt(LocalDateTime.now(clock).plusDays(1));
        em.persist(period);
        StudentGroup group = new StudentGroup(); group.setPeriod(period); group.setName("Supervised group");
        group.setCreatedBy(supervised); em.persist(group);
        GroupMember member = new GroupMember(); member.setGroup(group); member.setPeriod(period); member.setStudent(supervised);
        member.setSlotNo(1); member.setMemberRole(GroupMemberRole.LEADER); member.setJoinedAt(LocalDateTime.now(clock)); em.persist(member);
        Topic topic = new Topic(); topic.setPeriod(period); topic.setFaculty(department.getFaculty()); topic.setDepartment(department);
        topic.setCode(UUID.randomUUID().toString().substring(0, 20)); topic.setTitle("Supervised topic"); topic.setDescription("Description");
        topic.setObjectives("Objectives"); topic.setExpectedOutcomes("Outcomes"); topic.setProposedBy(lecturer);
        topic.setProposalSource(ProposalSource.LECTURER); topic.setVisibilityType(VisibilityType.OPEN); em.persist(topic);
        TopicSupervisor supervisor = new TopicSupervisor(); supervisor.setTopic(topic); supervisor.setLecturer(profile);
        supervisor.setSlotNo(1); supervisor.setAssignedBy(admin); supervisor.setAssignedAt(LocalDateTime.now(clock)); em.persist(supervisor);
        TopicRegistration registration = new TopicRegistration(); registration.setPeriod(period); registration.setGroup(group);
        registration.setTopic(topic); registration.setRegisteredBy(supervised); registration.setRegisteredAt(LocalDateTime.now(clock));
        registration.setStatus(RegistrationStatus.APPROVED); registration.setReviewedBy(admin);
        registration.setReviewedAt(LocalDateTime.now(clock)); registration.setDecisionReason("Fixture decision");
        em.persist(registration); em.flush();
        return new TeachingScope(profile, colleague, outsider, registration, member);
    }

    private Department department() {
        Faculty faculty = new Faculty(); faculty.setCode(UUID.randomUUID().toString().substring(0, 20));
        faculty.setName("Email faculty"); faculty.setActive(true); em.persist(faculty);
        Department department = new Department(); department.setFaculty(faculty);
        department.setCode(UUID.randomUUID().toString().substring(0, 20)); department.setName("Email department");
        department.setActive(true); em.persist(department); return department;
    }

    private Lecturer lecturerProfile(User account, Department department) {
        Lecturer profile = new Lecturer(); profile.setUser(account); profile.setDepartment(department);
        profile.setLecturerCode(UUID.randomUUID().toString().substring(0, 20)); em.persist(profile); return profile;
    }

    private record TeachingScope(Lecturer profile, User colleague, User outsider, TopicRegistration registration, GroupMember member) { }

    private User account(String name, Set<RoleCode> codes) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@dispatch.local");
        user.setFullName(name); user.setPasswordHash("unused"); user.setStatus(UserStatus.ACTIVE);
        user.replaceRoles(Set.copyOf(roles.findAllByCodeIn(codes)), LocalDateTime.now(clock));
        return users.save(user);
    }
}
