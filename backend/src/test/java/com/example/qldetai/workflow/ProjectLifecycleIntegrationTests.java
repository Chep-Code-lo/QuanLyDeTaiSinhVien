package com.example.qldetai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.AddCommitteeMemberRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateCommitteeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateDefenseRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateGradeChangeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseSessionResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.GradeChangeRequestResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultDetailResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ReviewGradeChangeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.AssessmentComponentResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateAssessmentComponentRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateEvaluationRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateRubricRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CriterionRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.EvaluationAssignmentResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.RubricResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.SaveSheetRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ScoreRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ScoreSheetResponse;
import com.example.qldetai.feature.evaluation.service.DefenseSessionService;
import com.example.qldetai.feature.evaluation.service.EvaluationAuthorization;
import com.example.qldetai.feature.evaluation.service.EvaluationCommitteeService;
import com.example.qldetai.feature.evaluation.service.EvaluationResultService;
import com.example.qldetai.feature.evaluation.service.EvaluationService;
import com.example.qldetai.feature.evaluation.service.GradeChangeService;
import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.AddGroupMemberRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.ReviewTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.StudentGroupResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.TopicRegistrationResponse;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.groupregistration.service.GroupService;
import com.example.qldetai.feature.groupregistration.service.RegistrationAuthorization;
import com.example.qldetai.feature.groupregistration.service.RegistrationService;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.dto.ProjectDtos.CreateAssignmentRequest;
import com.example.qldetai.feature.project.dto.ProjectDtos.ProjectAssignmentResponse;
import com.example.qldetai.feature.project.dto.ProjectDtos.UpdateAssignmentStatusRequest;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.project.service.ProjectAssignmentService;
import com.example.qldetai.feature.project.service.ProjectAuthorization;
import com.example.qldetai.feature.topic.domain.TopicReviewDecision;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.domain.VisibilityType;
import com.example.qldetai.feature.topic.dto.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.PublishTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.ReviewTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.TopicResponse;
import com.example.qldetai.feature.topic.service.TopicAuthorization;
import com.example.qldetai.feature.topic.service.TopicService;
import com.example.qldetai.security.UserPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.example.qldetai.feature.communication.email.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class ProjectLifecycleIntegrationTests {

    @DynamicPropertySource
    static void isolatedResultEmailMysql(DynamicPropertyRegistry properties) {
        String url = System.getProperty("result.email.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/ql_de_tai_sinh_vien?")) {
                throw new IllegalArgumentException("Result email tests require a disposable local database.");
            }
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add("spring.datasource.username", () -> "root");
            properties.add("spring.datasource.password", () -> "isolated-email-test");
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        }
    }

    @Autowired private com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository evaluationRepository;
    @Autowired private com.example.qldetai.feature.evaluation.repository.ResultVersionRepository resultRepository;
    @Autowired private AcademicAppointmentRepository appointments;
    @Autowired private EmailNotificationSettingsService emailSettings;
    @Autowired private EmailOutboxRepository emailOutbox;
    @Autowired private EmailNotificationEventService emailEvents;
    @Autowired private EmailEventContextFactory emailContexts;
    @Autowired private com.example.qldetai.feature.communication.service.NotificationService notifications;

    @ParameterizedTest
    @CsvSource({
        "GRADUATION,2027-05-01T01:00:00,2027-05-01T02:30:00,201",
        "GRADUATION,2027-04-30T17:00:00,2027-04-30T18:00:00,201",
        "GRADUATION,2027-04-30T23:30:00,2027-05-01T00:30:00,201",
        "GRADUATION,2027-04-30T01:00:00,2027-04-30T02:00:00,400",
        "GRADUATION,2027-05-02T01:00:00,2027-05-02T02:00:00,400",
        "GRADUATION,2027-05-01T16:30:00,2027-05-01T17:30:00,400",
        "GRADUATION,2027-05-01T16:30:00,2027-05-01T17:00:00,400",
        "GRADUATION,2027-04-30T16:30:00,2027-04-30T17:30:00,400",
        "COURSE,2027-05-02T01:00:00,2027-05-02T02:00:00,201",
        "SPECIALIZED,2027-05-02T01:00:00,2027-05-02T02:00:00,201",
        "RESEARCH,2027-05-02T01:00:00,2027-05-02T02:00:00,201"
    })
    void defenseDateIsEnforcedOnlyForGraduationUsingVietnamCalendarDates(
            ProjectType type, LocalDateTime start, LocalDateTime end, int expectedStatus,
            @Autowired MockMvc mvc) throws Exception {
        var project = createAssignedProject("defense-date", leaderStudent, secondStudent);
        Long committeeId = evaluationCommitteeService.createCommittee(
                new CreateCommitteeRequest(period.getId(), "Date validation"), admin.getId());
        evaluationCommitteeService.addMember(committeeId,
                new AddCommitteeMemberRequest(lecturerThree.getId(), 1, CommitteeRole.CHAIR), admin.getId());
        evaluationCommitteeService.addMember(committeeId,
                new AddCommitteeMemberRequest(lecturerFour.getId(), 2, CommitteeRole.SECRETARY), admin.getId());
        evaluationCommitteeService.addMember(committeeId,
                new AddCommitteeMemberRequest(lecturerFive.getId(), 3, CommitteeRole.MEMBER), admin.getId());
        period.setProjectType(type);
        period.setDefenseDate(LocalDate.of(2027, 5, 1));
        var result = mvc.perform(post("/api/v1/defense-sessions")
                .with(user(UserPrincipal.from(admin))).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"assignmentId":%d,"committeeId":%d,"location":"A101","startsAt":"%sZ","endsAt":"%sZ"}
                        """.formatted(project.id(), committeeId, start, end)))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 400) {
            result.andExpect(jsonPath("$.message").value(
                    "Phiên bảo vệ phải diễn ra vào ngày 01/05/2027 theo cấu hình đợt KLTN."));
            assertThat(defenseSessionService.listDefensesForStudent(leaderStudent.getUser().getId())).isEmpty();
            assertThat(notifications.list(leaderStudent.getUser().getId(), 0, 100).content())
                    .noneMatch(item -> item.title().equals("Lịch bảo vệ đề tài"));
        } else {
            assertThat(defenseSessionService.listDefensesForStudent(leaderStudent.getUser().getId()))
                    .singleElement().satisfies(item -> {
                        assertThat(item.startsAt()).isEqualTo(start);
                        assertThat(item.endsAt()).isEqualTo(end);
                    });
        }
    }

    @Test
    void rejectionNotifiesEveryCurrentMemberWithTheEnteredReason() {
        var topic = createPublishedTopic("rejected-reason");
        var group = createActiveGroup("Rejected group", leaderStudent, secondStudent);
        var registration = registrationService.create(
                new CreateTopicRegistrationRequest(group.id(), topic.id(), null),
                leaderStudent.getUser().getId());
        String reason = "Đề tài chưa phù hợp với định hướng của nhóm.";
        var rejected = registrationService.review(registration.id(),
                new ReviewTopicRegistrationRequest(registration.version(), RegistrationStatus.REJECTED, "  " + reason + "  "),
                admin.getId());
        assertThat(rejected.decisionReason()).isEqualTo(reason);
        for (var student : List.of(leaderStudent, secondStudent)) {
            var received = notifications.list(student.getUser().getId(), 0, 100).content();
            assertThat(received).filteredOn(item -> item.title().equals("Kết quả đăng ký đề tài"))
                    .singleElement().satisfies(item -> {
                        assertThat(item.content()).contains("đã bị từ chối", "Lý do: " + reason);
                        assertThat(item.readAt()).isNull();
                    });
        }
        assertThat(notifications.list(thirdStudent.getUser().getId(), 0, 100).content()).isEmpty();
    }

    @Test
    void publishedResultQueuesPersonalizedScoreEmailsExactlyOnce() {
        configureResultEmail(true);
        var project = createAssignedProject("emailed-score", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("email-score", "Scored", AssessmentComponentType.OTHER);
        submitFullScore(project.id(), catalog, lecturerThree, null);
        var finalized = evaluationResultService.finalizeResult(project.id(), admin.getId());
        assertThat(emailOutbox.count()).isZero();
        assertThatThrownBy(() -> emailContexts.resultPublished(resultRepository.findById(finalized.id()).orElseThrow()))
                .isInstanceOf(BusinessRuleException.class);
        evaluationResultService.publish(finalized.id(), admin.getId());
        assertThat(emailOutbox.findAll()).hasSize(2).allSatisfy(message -> {
            assertThat(message.getEventType()).isEqualTo(EmailNotificationEventType.RESULT_PUBLISHED);
            assertThat(message.getEventKey()).isEqualTo("result-published:" + finalized.id());
            assertThat(message.getBody()).contains("10.00/10", "/results", message.getRecipientName());
            assertThat(message.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        });
        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getRecipientEmail)
                .containsExactlyInAnyOrder(leaderStudent.getUser().getEmail(), secondStudent.getUser().getEmail());
        emailEvents.resultPublished(resultRepository.findById(finalized.id()).orElseThrow());
        assertThat(emailOutbox.count()).isEqualTo(2);
        assertThatThrownBy(() -> evaluationResultService.publish(finalized.id(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(emailOutbox.count()).isEqualTo(2);
    }

    @Test
    void disabledResultEmailDoesNotPreventPublicationOrQueueMessages() {
        configureResultEmail(false);
        var project = createAssignedProject("disabled-email", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("disabled-email", "Scored", AssessmentComponentType.OTHER);
        submitFullScore(project.id(), catalog, lecturerThree, null);
        var finalized = evaluationResultService.finalizeResult(project.id(), admin.getId());
        assertThat(evaluationResultService.publish(finalized.id(), admin.getId()).status().name()).isEqualTo("PUBLISHED");
        assertThat(emailOutbox.count()).isZero();
    }

    @Test
    void resultEmailUsesSnapshotMembersEvenIfCurrentMembershipChanges() {
        configureResultEmail(true);
        var project = createAssignedProject("snapshot-email", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("snapshot-email", "Scored", AssessmentComponentType.OTHER);
        submitFullScore(project.id(), catalog, lecturerThree, null);
        var finalized = evaluationResultService.finalizeResult(project.id(), admin.getId());
        var projectEntity = projectAssignmentRepository.findById(project.id()).orElseThrow();
        groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(projectEntity.getGroup().getId())
                .forEach(member -> member.setLeftAt(nowUtc()));
        groupMemberRepository.flush();
        evaluationResultService.publish(finalized.id(), admin.getId());
        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getRecipientEmail)
                .containsExactlyInAnyOrder(leaderStudent.getUser().getEmail(), secondStudent.getUser().getEmail());
    }

    private void configureResultEmail(boolean enabled) {
        var defaults = emailSettings.get(admin.getId(), EmailNotificationEventType.RESULT_PUBLISHED, NotificationScopeType.SYSTEM, 0L);
        emailSettings.update(admin.getId(), EmailNotificationEventType.RESULT_PUBLISHED,
                new EmailNotificationDtos.UpdateNotificationSettingRequest(NotificationScopeType.SYSTEM, 0L, enabled,
                        defaults.senderName(), defaults.fromEmail(), null, defaults.subjectTemplate(), defaults.bodyTemplate(),
                        defaults.recipientGroups(), null));
    }

    private final TopicService topicService;
    private final GroupService groupService;
    private final RegistrationService registrationService;
    private final ProjectAssignmentService projectAssignmentService;
    private final EvaluationService evaluationService;
    private final EvaluationCommitteeService evaluationCommitteeService;
    private final DefenseSessionService defenseSessionService;
    private final EvaluationResultService evaluationResultService;
    private final GradeChangeService gradeChangeService;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    ProjectLifecycleIntegrationTests(
            TopicService topicService,
            GroupService groupService,
            RegistrationService registrationService,
            ProjectAssignmentService projectAssignmentService,
            EvaluationService evaluationService,
            EvaluationCommitteeService evaluationCommitteeService,
            DefenseSessionService defenseSessionService,
            EvaluationResultService evaluationResultService,
            GradeChangeService gradeChangeService,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            SemesterRepository semesterRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            GroupMemberRepository groupMemberRepository,
            StudentGroupRepository studentGroupRepository,
            TopicRegistrationRepository topicRegistrationRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.topicService = topicService;
        this.groupService = groupService;
        this.registrationService = registrationService;
        this.projectAssignmentService = projectAssignmentService;
        this.evaluationService = evaluationService;
        this.evaluationCommitteeService = evaluationCommitteeService;
        this.defenseSessionService = defenseSessionService;
        this.evaluationResultService = evaluationResultService;
        this.gradeChangeService = gradeChangeService;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.topicRegistrationRepository = topicRegistrationRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    private Faculty faculty;
    private Department department;
    private Semester semester;
    private RegistrationPeriod period;
    private User admin;
    private Lecturer lecturerOne;
    private Lecturer lecturerTwo;
    private Lecturer lecturerThree;
    private Lecturer lecturerFour;
    private Lecturer lecturerFive;
    private Student leaderStudent;
    private Student secondStudent;
    private Student thirdStudent;
    private Student fourthStudent;

    @BeforeEach
    void setUp() {
        faculty = createFaculty("CNTT");
        department = createDepartment(faculty, "KTPM");
        semester = createSemester();
        admin = createUser("admin", "Backend 2 Admin", Set.of(RoleCode.ADMIN));
        lecturerOne = createLecturer("GV01", "Giảng viên 1");
        lecturerTwo = createLecturer("GV02", "Giảng viên 2");
        lecturerThree = createLecturer("GV03", "Giảng viên 3");
        lecturerFour = createLecturer("GV04", "Giảng viên 4");
        lecturerFive = createLecturer("GV05", "Giảng viên 5");
        leaderStudent = createStudent("SV01", "Sinh viên trưởng nhóm");
        secondStudent = createStudent("SV02", "Sinh viên 2");
        thirdStudent = createStudent("SV03", "Sinh viên 3");
        fourthStudent = createStudent("SV04", "Sinh viên 4");
        period = createActivePeriod();
    }

    @Test
    void topicLifecycleCanGoFromProposalToPublished() {
        TopicResponse created =
                topicService.create(
                        new CreateTopicRequest(
                                period.getId(),
                                "dt-01",
                                "Xây dựng API backend 2",
                                "Mô tả đề tài",
                                "Hoàn thiện các API nghiệp vụ",
                                "Có thể ghép với frontend",
                                "Biết Spring Boot",
                                VisibilityType.OPEN,
                                null),
                        lecturerOne.getUser().getId());

        assertThat(created.code()).isEqualTo("DT-01");
        assertThat(created.status()).isEqualTo(TopicStatus.PENDING);
        assertThat(created.proposalSource().name()).isEqualTo("LECTURER");

        TopicResponse reviewed =
                topicService.review(
                        created.id(),
                        new ReviewTopicRequest(
                                created.version(),
                                TopicReviewDecision.APPROVED,
                                "Đề tài đạt yêu cầu."),
                        admin.getId());

        assertThat(reviewed.status()).isEqualTo(TopicStatus.APPROVED);
        assertThat(reviewed.latestReview()).isNotNull();
        assertThat(reviewed.latestReview().decision()).isEqualTo(TopicReviewDecision.APPROVED);

        TopicResponse assigned =
                topicService.assignSupervisors(
                        reviewed.id(),
                        new AssignSupervisorsRequest(
                                reviewed.version(), lecturerOne.getId(), lecturerTwo.getId()),
                        admin.getId());

        assertThat(assigned.supervisors()).hasSize(2);
        assertThat(assigned.supervisors().getFirst().slotNo()).isEqualTo(1);
        assertThat(assigned.supervisors().get(1).slotNo()).isEqualTo(2);

        TopicResponse published =
                topicService.publish(
                        assigned.id(), new PublishTopicRequest(assigned.version()), admin.getId());

        assertThat(published.status()).isEqualTo(TopicStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.supervisors()).hasSize(2);
    }

    @Test
    void groupMemberSlotIsReusedAfterRemovingAMember() {
        StudentGroupResponse created =
                groupService.create(
                        new CreateGroupRequest(period.getId(), "Nhóm backend 2"),
                        leaderStudent.getUser().getId());

        groupService.addMember(
                created.id(),
                new AddGroupMemberRequest(secondStudent.getStudentCode()),
                leaderStudent.getUser().getId());
        groupService.addMember(
                created.id(),
                new AddGroupMemberRequest(thirdStudent.getStudentCode()),
                leaderStudent.getUser().getId());
        groupService.removeMember(
                created.id(), secondStudent.getId(), leaderStudent.getUser().getId());

        StudentGroupResponse updated =
                groupService.addMember(
                        created.id(),
                        new AddGroupMemberRequest(fourthStudent.getStudentCode()),
                        leaderStudent.getUser().getId());

        List<GroupMember> activeMembers =
                groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(created.id());
        assertThat(updated.status()).isEqualTo(GroupStatus.ACTIVE);
        assertThat(activeMembers).hasSize(3);
        assertThat(activeMembers).extracting(GroupMember::getSlotNo).containsExactly(1, 2, 3);
        assertThat(activeMembers)
                .extracting(member -> member.getStudent().getStudentCode())
                .containsExactly(
                        leaderStudent.getStudentCode(),
                        fourthStudent.getStudentCode(),
                        thirdStudent.getStudentCode());
    }

    @Test
    void topicRegistrationCanBeSubmittedAndApproved() {
        TopicResponse topic = createPublishedTopic("dt-reg-01");
        StudentGroupResponse group =
                createActiveGroup("Nhóm đăng ký", leaderStudent, secondStudent);

        TopicRegistrationResponse created =
                registrationService.create(
                        new CreateTopicRegistrationRequest(
                                group.id(), topic.id(), "Nhóm phù hợp với đề tài này."),
                        leaderStudent.getUser().getId());

        assertThat(created.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(created.topicCode()).isEqualTo(topic.code());

        TopicRegistrationResponse reviewed =
                registrationService.review(
                        created.id(),
                        new ReviewTopicRegistrationRequest(
                                created.version(), RegistrationStatus.APPROVED, null),
                        admin.getId());

        assertThat(reviewed.status()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(reviewed.reviewedBy()).isEqualTo(admin.getId());
        assertThat(reviewed.decisionReason()).isNull();
        assertThat(projectAssignmentRepository.findByRegistrationId(reviewed.id()))
                .isPresent()
                .get()
                .extracting(project -> project.getStatus())
                .isEqualTo(AssignmentStatus.IN_PROGRESS);
    }

    @Test
    void assignmentCreationRejectsOtherPendingRegistrationAndCompletionClosesGroup() {
        TopicResponse topic = createPublishedTopic("dt-assign-01");
        StudentGroupResponse firstGroup = createActiveGroup("Nhóm A", leaderStudent, secondStudent);
        StudentGroupResponse secondGroup = createActiveGroup("Nhóm B", thirdStudent, fourthStudent);

        TopicRegistrationResponse approvedRegistration =
                registrationService.create(
                        new CreateTopicRegistrationRequest(
                                firstGroup.id(), topic.id(), "Nhóm A đăng ký."),
                        leaderStudent.getUser().getId());

        TopicRegistrationResponse pendingRegistration =
                registrationService.create(
                        new CreateTopicRegistrationRequest(
                                secondGroup.id(), topic.id(), "Nhóm B đăng ký."),
                        thirdStudent.getUser().getId());

        approvedRegistration =
                registrationService.review(
                        approvedRegistration.id(),
                        new ReviewTopicRegistrationRequest(
                                approvedRegistration.version(), RegistrationStatus.APPROVED, null),
                        admin.getId());

        ProjectAssignmentResponse created =
                projectAssignmentService.create(
                        new CreateAssignmentRequest(approvedRegistration.id()), admin.getId());

        assertThat(created.status()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(projectAssignmentRepository.existsByGroupId(firstGroup.id())).isTrue();
        assertThat(studentGroupRepository.findById(firstGroup.id()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.LOCKED);

        TopicRegistration rejectedRegistration =
                topicRegistrationRepository.findById(pendingRegistration.id()).orElseThrow();
        assertThat(rejectedRegistration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);

        ProjectAssignmentResponse completed =
                projectAssignmentService.updateStatus(
                        created.id(),
                        new UpdateAssignmentStatusRequest(
                                created.version(), AssignmentStatus.COMPLETED),
                        admin.getId());

        assertThat(completed.status()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(studentGroupRepository.findById(firstGroup.id()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.CLOSED);
    }

    @Test
    void assessmentCatalogCanBeLockedBeforeEvaluatorAssignment() {
        LockedCatalog catalog =
                createLockedCatalog("rv-01", "Chấm phản biện", AssessmentComponentType.REVIEW);

        assertThat(catalog.component().componentType()).isEqualTo(AssessmentComponentType.REVIEW);
        assertThat(catalog.component().status()).isEqualTo("LOCKED");
        assertThat(catalog.rubric().status()).isEqualTo("LOCKED");
        assertThat(catalog.rubric().criteria())
                .extracting(criterion -> criterion.maxScore())
                .containsExactly(new BigDecimal("6.00"), new BigDecimal("4.00"));
    }

    @Test
    void committeeConfigurationCanBeSavedOverHttp(@Autowired MockMvc mvc) throws Exception {
        LockedCatalog catalog =
                createLockedCatalog("committee-config", "Chấm hội đồng", AssessmentComponentType.COMMITTEE);
        String body = """
                {"periodId":%d,"name":"Hội đồng 1","rubricId":%d,"members":[
                  {"lecturerId":%d,"roleName":"Chủ tịch"},
                  {"lecturerId":%d,"roleName":"Thư ký"},
                  {"lecturerId":%d,"roleName":"Ủy viên chuyên môn"}
                ]}
                """.formatted(
                period.getId(),
                catalog.rubric().id(),
                lecturerThree.getId(),
                lecturerFour.getId(),
                lecturerFive.getId());

        mvc.perform(post("/api/v1/evaluation-committees/configuration")
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Hội đồng 1"))
                .andExpect(jsonPath("$.data.rubricId").value(catalog.rubric().id()))
                .andExpect(jsonPath("$.data.members[2].roleName").value("Ủy viên chuyên môn"));

        List<EvaluationCommitteeResponse> savedCommittees =
                evaluationCommitteeService.listCommittees(period.getId(), 0, 10).content();
        assertThat(savedCommittees)
                .singleElement()
                .satisfies(committee -> {
                    assertThat(committee.rubricId()).isEqualTo(catalog.rubric().id());
                    assertThat(committee.members()).hasSize(3);
                });
        EvaluationCommitteeResponse created = savedCommittees.get(0);
        String updatedBody = """
                {"periodId":%d,"name":"Hội đồng đã cập nhật","rubricId":%d,"version":%d,"members":[
                  {"lecturerId":%d,"roleName":"Chủ tịch"},
                  {"lecturerId":%d,"roleName":"Thư ký"},
                  {"lecturerId":%d,"roleName":"Ủy viên chuyên môn"}
                ]}
                """.formatted(
                period.getId(),
                catalog.rubric().id(),
                created.version(),
                lecturerThree.getId(),
                lecturerFour.getId(),
                lecturerFive.getId());

        mvc.perform(put("/api/v1/evaluation-committees/{id}/configuration", created.id())
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updatedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Hội đồng đã cập nhật"))
                .andExpect(jsonPath("$.data.version").value(created.version() + 1));

        String rosterOnlyUpdateBody = """
                {"periodId":%d,"name":"Hội đồng đã cập nhật","rubricId":%d,"version":%d,"members":[
                  {"lecturerId":%d,"roleName":"Chủ tịch"},
                  {"lecturerId":%d,"roleName":"Thư ký"},
                  {"lecturerId":%d,"roleName":"Ủy viên phản biện"}
                ]}
                """.formatted(
                period.getId(),
                catalog.rubric().id(),
                created.version() + 1,
                lecturerThree.getId(),
                lecturerFour.getId(),
                lecturerFive.getId());
        mvc.perform(put("/api/v1/evaluation-committees/{id}/configuration", created.id())
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(rosterOnlyUpdateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(created.version() + 2))
                .andExpect(jsonPath("$.data.members[2].roleName").value("Ủy viên phản biện"));

        mvc.perform(put("/api/v1/evaluation-committees/{id}/configuration", created.id())
                        .with(user(UserPrincipal.from(admin)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updatedBody))
                .andExpect(status().isConflict());
    }

    @Test
    void committeeEvaluationAppearsInAssignmentAndDefenseReads() {
        ProjectAssignmentResponse assignment =
                createAssignedProject("dt-defense-01", leaderStudent, secondStudent);
        LockedCatalog catalog =
                createLockedCatalog("hd-01", "Chấm hội đồng", AssessmentComponentType.COMMITTEE);

        Long committeeId =
                evaluationCommitteeService.createCommittee(
                        new CreateCommitteeRequest(period.getId(), "Hội đồng demo"), admin.getId());
        evaluationCommitteeService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerThree.getId(), 1, CommitteeRole.CHAIR),
                admin.getId());
        evaluationCommitteeService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerFour.getId(), 2, CommitteeRole.SECRETARY),
                admin.getId());
        evaluationCommitteeService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerFive.getId(), 3, CommitteeRole.MEMBER),
                admin.getId());

        LocalDateTime startsAt = nowUtc().plusDays(5);
        Long defenseId =
                defenseSessionService.schedule(
                        new CreateDefenseRequest(
                                assignment.id(),
                                committeeId,
                                "Phòng A101",
                                startsAt,
                                startsAt.plusHours(1)),
                        admin.getId());

        for (var student : List.of(leaderStudent, secondStudent)) {
            assertThat(notifications.list(student.getUser().getId(), 0, 100).content())
                    .filteredOn(item -> item.title().equals("Lịch bảo vệ đề tài"))
                    .singleElement().satisfies(item -> {
                        assertThat(item.content()).contains(assignment.topicCode(), assignment.topicTitle(), "Phòng A101");
                        var localStart = startsAt.atOffset(java.time.ZoneOffset.UTC)
                                .atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
                        assertThat(item.content()).contains(localStart.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                        assertThat(item.targetPath()).isEqualTo("/results");
                        assertThat(item.readAt()).isNull();
                    });
        }
        assertThat(notifications.list(thirdStudent.getUser().getId(), 0, 100).content()).isEmpty();

        Long evaluationId =
                evaluationService.assign(
                        new CreateEvaluationRequest(
                                assignment.id(),
                                catalog.rubric().id(),
                                lecturerThree.getId(),
                                defenseId,
                                nowUtc().plusDays(7)),
                        admin.getId());

        PageResponse<EvaluationCommitteeResponse> committees =
                evaluationCommitteeService.listCommittees(period.getId(), 0, 10);
        PageResponse<DefenseSessionResponse> defenses =
                defenseSessionService.listDefenses(period.getId(), committeeId, 0, 10);
        PageResponse<EvaluationAssignmentResponse> evaluations =
                evaluationService.listAssignments(
                        period.getId(), assignment.id(), lecturerThree.getId(), null, 0, 10);

        assertThat(committeeId).isPositive();
        assertThat(defenseId).isPositive();
        assertThat(evaluationId).isPositive();
        assertThat(committees.content())
                .singleElement()
                .satisfies(
                        committee -> {
                            assertThat(committee.status().name()).isEqualTo("CONFIRMED");
                            assertThat(committee.members()).hasSize(3);
                        });
        assertThat(defenses.content())
                .singleElement()
                .satisfies(
                        defense -> {
                            assertThat(defense.id()).isEqualTo(defenseId);
                            assertThat(defense.assignmentId()).isEqualTo(assignment.id());
                            assertThat(defense.committeeId()).isEqualTo(committeeId);
                        });
        assertThat(evaluations.content())
                .singleElement()
                .satisfies(
                        evaluation -> {
                            assertThat(evaluation.id()).isEqualTo(evaluationId);
                            assertThat(evaluation.defenseId()).isEqualTo(defenseId);
                            assertThat(evaluation.evaluatorRole()).isEqualTo("COMMITTEE_MEMBER");
                            assertThat(evaluation.lecturerId()).isEqualTo(lecturerThree.getId());
                        });
    }

    @Test
    void defenseCannotBeScheduledUntilEveryReviewSheetIsSubmitted() {
        ProjectAssignmentResponse assignment =
                createAssignedProject("dt-review-gate-01", leaderStudent, secondStudent);
        LockedCatalog reviewCatalog =
                createLockedCatalog(
                        "review-gate-01", "Phản biện bắt buộc", AssessmentComponentType.REVIEW);
        evaluationService.assign(
                new CreateEvaluationRequest(
                        assignment.id(),
                        reviewCatalog.rubric().id(),
                        lecturerThree.getId(),
                        null,
                        nowUtc().plusDays(4)),
                admin.getId());

        Long committeeId =
                evaluationCommitteeService.createCommittee(
                        new CreateCommitteeRequest(period.getId(), "Hội đồng kiểm tra phản biện"),
                        admin.getId());
        evaluationCommitteeService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerThree.getId(), 1, CommitteeRole.CHAIR),
                admin.getId());
        evaluationCommitteeService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerFour.getId(), 2, CommitteeRole.SECRETARY),
                admin.getId());
        evaluationCommitteeService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerFive.getId(), 3, CommitteeRole.MEMBER),
                admin.getId());

        LocalDateTime startsAt = nowUtc().plusDays(5);
        assertThatThrownBy(
                        () ->
                                defenseSessionService.schedule(
                                        new CreateDefenseRequest(
                                                assignment.id(),
                                                committeeId,
                                                "Phòng A102",
                                                startsAt,
                                                startsAt.plusHours(1)),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Complete every reviewer assignment");
        for (var student : List.of(leaderStudent, secondStudent)) {
            assertThat(notifications.list(student.getUser().getId(), 0, 100).content())
                    .noneMatch(item -> item.title().equals("Lịch bảo vệ đề tài"));
        }
    }

    @Test
    void gradeChangeCannotReopenAScoreSheetUsedByAFinalizedResult() {
        ProjectAssignmentResponse assignment =
                createAssignedProject("dt-grade-change-01", leaderStudent, secondStudent);
        LockedCatalog catalog =
                createLockedCatalog("pb-01", "Chấm phản biện 1", AssessmentComponentType.REVIEW);

        Long evaluationId =
                evaluationService.assign(
                        new CreateEvaluationRequest(
                                assignment.id(),
                                catalog.rubric().id(),
                                lecturerThree.getId(),
                                null,
                                nowUtc().plusDays(4)),
                        admin.getId());

        ScoreSheetResponse submittedSheet =
                evaluationService.saveSheet(
                        evaluationId,
                        new SaveSheetRequest(
                                0,
                                "Bản chấm đầu tiên",
                                scoreRequests(
                                        catalog.rubric(),
                                        new BigDecimal("6.00"),
                                        new BigDecimal("4.00")),
                                true),
                        lecturerThree.getUser().getId());

        // The request is valid while the sheet is still submitted; finalization
        // may happen before an administrator decides whether to approve it.
        Long changeId =
                gradeChangeService.requestChange(
                        submittedSheet.id(),
                        new CreateGradeChangeRequest("Cần chỉnh lại nhận xét và điểm."),
                        lecturerThree.getUser().getId());
        ResultResponse finalized =
                evaluationResultService.finalizeResult(assignment.id(), admin.getId());

        assertThatThrownBy(
                        () ->
                                gradeChangeService.reviewChange(
                                        changeId,
                                        new ReviewGradeChangeRequest(
                                                true, "Cho phép sửa lại phiếu chấm."),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("captured by a result version");
        ResultResponse published = evaluationResultService.publish(finalized.id(), admin.getId());
        assertThatThrownBy(
                        () ->
                                gradeChangeService.requestChange(
                                        submittedSheet.id(),
                                        new CreateGradeChangeRequest(
                                                "Không được mở lại sau khi chốt."),
                                        lecturerThree.getUser().getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("captured by a result version");
        assertThatThrownBy(
                        () ->
                                evaluationService.saveSheet(
                                        evaluationId,
                                        new SaveSheetRequest(
                                                submittedSheet.version(),
                                                "Không được sửa nguồn điểm đã chốt",
                                                scoreRequests(
                                                        catalog.rubric(),
                                                        new BigDecimal("5.50"),
                                                        new BigDecimal("4.00")),
                                                true),
                                        lecturerThree.getUser().getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("captured by a result version");

        PageResponse<GradeChangeRequestResponse> allChanges =
                gradeChangeService.listChanges(null, submittedSheet.id(), 0, 10, admin.getId());

        assertThat(finalized.status().name()).isEqualTo("FINALIZED");
        assertThat(published.status().name()).isEqualTo("PUBLISHED");
        assertThat(allChanges.content())
                .singleElement()
                .satisfies(
                        change -> {
                            assertThat(change.id()).isEqualTo(changeId);
                            assertThat(change.status()).isEqualTo(GradeChangeStatus.PENDING);
                            assertThat(change.appliedAt()).isNull();
                            assertThat(change.reviewedAt()).isNull();
                        });
    }

    @Test
    void finalResultAveragesEachComponentBeforeAveragingTheComponentsAndKeepsSnapshots() {
        ProjectAssignmentResponse assignment =
                createAssignedProject("dt-result-formula-01", leaderStudent, secondStudent);
        LockedCatalog singleEvaluatorComponent =
                createLockedCatalog(
                        "other-formula-01",
                        "Điểm thành phần một người chấm",
                        AssessmentComponentType.OTHER);
        LockedCatalog twoEvaluatorComponent =
                createLockedCatalog(
                        "review-formula-01",
                        "Điểm thành phần hai người chấm",
                        AssessmentComponentType.REVIEW);

        Long singleEvaluator =
                evaluationService.assign(
                        new CreateEvaluationRequest(
                                assignment.id(),
                                singleEvaluatorComponent.rubric().id(),
                                lecturerThree.getId(),
                                null,
                                nowUtc().plusDays(4)),
                        admin.getId());
        Long firstReviewer =
                evaluationService.assign(
                        new CreateEvaluationRequest(
                                assignment.id(),
                                twoEvaluatorComponent.rubric().id(),
                                lecturerFour.getId(),
                                null,
                                nowUtc().plusDays(4)),
                        admin.getId());
        Long secondReviewer =
                evaluationService.assign(
                        new CreateEvaluationRequest(
                                assignment.id(),
                                twoEvaluatorComponent.rubric().id(),
                                lecturerFive.getId(),
                                null,
                                nowUtc().plusDays(4)),
                        admin.getId());

        evaluationService.saveSheet(
                singleEvaluator,
                new SaveSheetRequest(
                        0,
                        "8 điểm",
                        scoreRequests(
                                singleEvaluatorComponent.rubric(),
                                new BigDecimal("6.00"),
                                new BigDecimal("2.00")),
                        true),
                lecturerThree.getUser().getId());
        evaluationService.saveSheet(
                firstReviewer,
                new SaveSheetRequest(
                        0,
                        "2 điểm",
                        scoreRequests(
                                twoEvaluatorComponent.rubric(),
                                new BigDecimal("2.00"),
                                BigDecimal.ZERO),
                        true),
                lecturerFour.getUser().getId());
        evaluationService.saveSheet(
                secondReviewer,
                new SaveSheetRequest(
                        0,
                        "10 điểm",
                        scoreRequests(
                                twoEvaluatorComponent.rubric(),
                                new BigDecimal("6.00"),
                                new BigDecimal("4.00")),
                        true),
                lecturerFive.getUser().getId());

        ResultResponse finalized =
                evaluationResultService.finalizeResult(assignment.id(), admin.getId());
        ResultDetailResponse detail =
                evaluationResultService.getResult(finalized.id(), admin.getId());

        // (8 + average(2, 10)) / 2 = 7.00.  A mean across all three sheets would be 6.67.
        assertThat(finalized.finalScore()).isEqualByComparingTo("7.00");
        assertThat(detail.inputs()).hasSize(3);
        assertThat(detail.inputs())
                .allSatisfy(
                        input -> {
                            assertThat(input.sheetVersion()).isPositive();
                            assertThat(input.rubricSnapshot()).isNotBlank();
                        });
        assertThat(detail.members())
                .extracting(member -> member.studentId())
                .containsExactlyInAnyOrder(leaderStudent.getId(), secondStudent.getId());
        assertThat(
                        evaluationService
                                .mySheet(singleEvaluator, lecturerThree.getUser().getId())
                                .status())
                .isEqualTo(ScoreSheetStatus.LOCKED);
    }

    @Test
    void resourceAuthorizationKeepsFacultyScopeAfterExtraction(
            @Autowired AcademicAppointmentRepository appointments,
            @Autowired TopicAuthorization topicAuthorization,
            @Autowired RegistrationAuthorization registrationAuthorization,
            @Autowired ProjectAuthorization projectAuthorization,
            @Autowired EvaluationAuthorization evaluationAuthorization,
            @Autowired MockMvc mvc)
            throws Exception {
        ProjectAssignmentResponse assigned =
                createAssignedProject("AUTH", leaderStudent, secondStudent);
        var project = projectAssignmentRepository.findById(assigned.id()).orElseThrow();
        var catalog =
                createLockedCatalog(
                        "AUTH", "Authorization rubric", AssessmentComponentType.REVIEW);
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(lecturerOne);
        appointment.setFaculty(faculty);
        appointment.setPositionCode(PositionCode.DEAN);
        appointment.setStartsAt(nowUtc().minusDays(1));
        appointment.setAssignedBy(admin);
        appointments.saveAndFlush(appointment);
        UserPrincipal principal = UserPrincipal.from(lecturerOne.getUser());
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());

        assertThat(topicAuthorization.canManageTopic(project.getTopic().getId(), authentication))
                .isTrue();
        assertThat(
                        registrationAuthorization.canManageRegistration(
                                project.getRegistration().getId(), authentication))
                .isTrue();
        assertThat(projectAuthorization.canManageAssignment(project.getId(), authentication))
                .isTrue();
        assertThat(evaluationAuthorization.canManageRubric(catalog.rubric().id(), authentication))
                .isTrue();
        assertThat(projectAuthorization.canManageAssignment(Long.MAX_VALUE, authentication))
                .isFalse();
        assertThat(topicAuthorization.canManageTopic(project.getTopic().getId(), null)).isFalse();

        String request = "{\"periodId\":" + period.getId() + ",\"name\":\"Scope committee\"}";
        mvc.perform(
                        post("/api/v1/evaluation-committees")
                                .with(user(principal))
                                .with(csrf())
                                .contentType("application/json")
                                .content(request))
                .andExpect(status().isCreated());

        // An appointment in another faculty cannot grant access to these same resources.
        appointment.setFaculty(createFaculty("OTHER"));
        appointments.saveAndFlush(appointment);
        assertThat(topicAuthorization.canManageTopic(project.getTopic().getId(), authentication))
                .isFalse();
        assertThat(
                        registrationAuthorization.canManageRegistration(
                                project.getRegistration().getId(), authentication))
                .isFalse();
        assertThat(projectAuthorization.canManageAssignment(project.getId(), authentication))
                .isFalse();
        assertThat(evaluationAuthorization.canManageRubric(catalog.rubric().id(), authentication))
                .isFalse();
        mvc.perform(
                        post("/api/v1/evaluation-committees")
                                .with(user(principal))
                                .with(csrf())
                                .contentType("application/json")
                                .content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void deanCanReadMilestonesForTheirFacultyOnly(@Autowired MockMvc mvc) throws Exception {
        ProjectAssignmentResponse assigned =
                createAssignedProject("MILESTONE-SCOPE", leaderStudent, secondStudent);
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(lecturerFour);
        appointment.setFaculty(faculty);
        appointment.setPositionCode(PositionCode.DEAN);
        appointment.setStartsAt(nowUtc().minusDays(1));
        appointment.setAssignedBy(admin);
        appointments.saveAndFlush(appointment);

        UserPrincipal dean = UserPrincipal.from(lecturerFour.getUser());
        String endpoint = "/api/v1/project-assignments/" + assigned.id() + "/milestones";
        mvc.perform(get(endpoint).with(user(dean)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        appointment.setFaculty(createFaculty("OTHER"));
        appointments.saveAndFlush(appointment);
        mvc.perform(get(endpoint).with(user(dean))).andExpect(status().isForbidden());
    }

    @Test
    void regroupedEvaluationReadEndpointsRemainAvailable(@Autowired MockMvc mvc) throws Exception {
        UserPrincipal principal = UserPrincipal.from(admin);
        for (String resource :
                List.of(
                        "assessment-components",
                        "rubrics",
                        "evaluation-assignments",
                        "evaluation-committees",
                        "defense-sessions",
                        "results",
                        "grade-change-requests")) {
            var request = get("/api/v1/" + resource);
            if ("assessment-components".equals(resource)) {
                request.param("periodId", period.getId().toString());
            } else if ("rubrics".equals(resource)) {
                request.param("componentId", "0");
            }
            mvc.perform(request.with(user(principal)))
                    .andExpect(status().isOk());
        }
    }

    private TopicResponse createPublishedTopic(String code) {
        TopicResponse created =
                topicService.create(
                        new CreateTopicRequest(
                                period.getId(),
                                code,
                                "Đề tài " + code,
                                "Mô tả " + code,
                                "Mục tiêu " + code,
                                "Kết quả " + code,
                                null,
                                VisibilityType.OPEN,
                                null),
                        lecturerOne.getUser().getId());
        TopicResponse reviewed =
                topicService.review(
                        created.id(),
                        new ReviewTopicRequest(
                                created.version(), TopicReviewDecision.APPROVED, "Đạt."),
                        admin.getId());
        TopicResponse assigned =
                topicService.assignSupervisors(
                        reviewed.id(),
                        new AssignSupervisorsRequest(
                                reviewed.version(), lecturerOne.getId(), lecturerTwo.getId()),
                        admin.getId());
        return topicService.publish(
                assigned.id(), new PublishTopicRequest(assigned.version()), admin.getId());
    }

    @Test
    void scoringRejectsExpiredDeadlinesAndCancelledEvaluations() {
        var project = createAssignedProject("deadline", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("deadline", "Deadline", AssessmentComponentType.OTHER);
        Long evaluationId = evaluationService.assign(new CreateEvaluationRequest(project.id(),
                catalog.rubric().id(), lecturerThree.getId(), null, nowUtc().plusDays(1)), admin.getId());
        var evaluation = evaluationRepository.findById(evaluationId).orElseThrow();
        evaluation.setDeadlineAt(nowUtc().minusSeconds(1));
        var request = new SaveSheetRequest(0, "Test", scoreRequests(catalog.rubric(),
                new BigDecimal("6"), new BigDecimal("4")), true);
        assertThatThrownBy(() -> evaluationService.saveSheet(evaluationId, request, lecturerThree.getUser().getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("deadline");
        evaluation.setDeadlineAt(nowUtc().plusDays(1));
        evaluation.setStatus(com.example.qldetai.feature.evaluation.domain.EvaluationStatus.CANCELLED);
        assertThatThrownBy(() -> evaluationService.saveSheet(evaluationId, request, lecturerThree.getUser().getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("active evaluation");
    }

    @Test
    void reviewScoringHonorsPeriodDeadlineAndRejectsAssignmentsBeyondIt() {
        var project = createAssignedProject("review-deadline", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("review-deadline", "Review", AssessmentComponentType.REVIEW);
        assertThatThrownBy(() -> evaluationService.assign(new CreateEvaluationRequest(project.id(),
                catalog.rubric().id(), lecturerThree.getId(), null, period.getReviewDeadlineAt().plusDays(1)), admin.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot exceed");
        Long id = evaluationService.assign(new CreateEvaluationRequest(project.id(), catalog.rubric().id(),
                lecturerThree.getId(), null, nowUtc().plusDays(1)), admin.getId());
        period.setReviewDeadlineAt(nowUtc().minusSeconds(1));
        assertThatThrownBy(() -> evaluationService.saveSheet(id, new SaveSheetRequest(0, "Late",
                scoreRequests(catalog.rubric(), new BigDecimal("6"), new BigDecimal("4")), true),
                lecturerThree.getUser().getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("review deadline");
    }

    @Test
    void studentCanCreateOrJoinAGroupInAnotherRegistrationPeriod() {
        var group = createActiveGroup("Existing", leaderStudent, secondStudent);
        studentGroupRepository.findById(group.id()).orElseThrow().setStatus(GroupStatus.LOCKED);
        var otherPeriod = createActivePeriod();

        assertThat(groupService.create(new CreateGroupRequest(otherPeriod.getId(), "Other"),
                leaderStudent.getUser().getId()).id()).isPositive();
        var otherGroup = groupService.create(new CreateGroupRequest(otherPeriod.getId(), "Other"),
                thirdStudent.getUser().getId());
        assertThat(groupService.addMember(otherGroup.id(),
                new AddGroupMemberRequest(secondStudent.getStudentCode()), thirdStudent.getUser().getId()).id())
                .isEqualTo(otherGroup.id());

        assertThatThrownBy(() -> groupService.create(new CreateGroupRequest(otherPeriod.getId(), "Duplicate"),
                leaderStudent.getUser().getId())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active group");
    }

    @Test
    void closedOrCancelledPeriodRejectsTopicRegistrationEvenInsideDateWindow() {
        var topic = createPublishedTopic("closed-period");
        var group = createActiveGroup("Group", leaderStudent, secondStudent);
        for (var state : List.of(RegistrationPeriodStatus.CLOSED, RegistrationPeriodStatus.CANCELLED)) {
            period.setStatus(state);
            assertThatThrownBy(() -> registrationService.create(
                    new CreateTopicRegistrationRequest(group.id(), topic.id(), "Test"), leaderStudent.getUser().getId()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("active registration period");
        }
        period.setStatus(RegistrationPeriodStatus.ACTIVE);
        assertThat(registrationService.create(new CreateTopicRegistrationRequest(group.id(), topic.id(), "Test"),
                leaderStudent.getUser().getId()).status()).isEqualTo(RegistrationStatus.PENDING);
    }

    @Test
    void finalizationRejectsAComponentWithNoAssignedEvaluator() {
        var project = createAssignedProject("missing-component", leaderStudent, secondStudent);
        var scored = createLockedCatalog("scored", "Scored", AssessmentComponentType.OTHER);
        createLockedCatalog("missing", "Missing", AssessmentComponentType.REVIEW);
        submitFullScore(project.id(), scored, lecturerThree, null);
        assertThatThrownBy(() -> evaluationResultService.finalizeResult(project.id(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("evaluator assignment is missing");
        assertThat(resultRepository.countByAssignmentId(project.id())).isZero();
    }

    @Test
    void onlyTheCorrespondingChairCanFinalizeACommitteeResultThroughHttp(@Autowired MockMvc mvc) throws Exception {
        var project = createAssignedProject("chair-only", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("committee", "Committee", AssessmentComponentType.COMMITTEE);
        Long committeeId = evaluationCommitteeService.createCommittee(
                new CreateCommitteeRequest(period.getId(), "Council"), admin.getId());
        evaluationCommitteeService.addMember(committeeId,
                new AddCommitteeMemberRequest(lecturerThree.getId(), 1, CommitteeRole.CHAIR), admin.getId());
        evaluationCommitteeService.addMember(committeeId,
                new AddCommitteeMemberRequest(lecturerFour.getId(), 2, CommitteeRole.SECRETARY), admin.getId());
        evaluationCommitteeService.addMember(committeeId,
                new AddCommitteeMemberRequest(lecturerFive.getId(), 3, CommitteeRole.MEMBER), admin.getId());
        Long defenseId = defenseSessionService.schedule(new CreateDefenseRequest(project.id(), committeeId,
                "A101", nowUtc().plusDays(2), nowUtc().plusDays(2).plusHours(1)), admin.getId());
        submitFullScore(project.id(), catalog, lecturerThree, defenseId);
        submitFullScore(project.id(), catalog, lecturerFour, defenseId);
        submitFullScore(project.id(), catalog, lecturerFive, defenseId);
        Long otherCommittee = evaluationCommitteeService.createCommittee(
                new CreateCommitteeRequest(period.getId(), "Unrelated council"), admin.getId());
        evaluationCommitteeService.addMember(otherCommittee,
                new AddCommitteeMemberRequest(lecturerOne.getId(), 1, CommitteeRole.CHAIR), admin.getId());
        String url = "/api/v1/project-assignments/" + project.id() + "/results/finalize";
        for (User denied : List.of(admin, lecturerFour.getUser(), lecturerOne.getUser())) {
            mvc.perform(post(url).with(user(UserPrincipal.from(denied))).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post(url).with(user(UserPrincipal.from(lecturerThree.getUser()))).with(csrf()))
                .andExpect(status().isOk());
        assertThat(resultRepository.countByAssignmentId(project.id())).isEqualTo(1);
    }

    @Test
    void resultListsAndDetailsAreScopedAndStudentsOnlyReadPublishedSnapshots(@Autowired MockMvc mvc) throws Exception {
        var project = createAssignedProject("scoped-results", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("scoped", "Scored", AssessmentComponentType.OTHER);
        submitFullScore(project.id(), catalog, lecturerThree, null);
        var finalized = evaluationResultService.finalizeResult(project.id(), admin.getId());
        assertThat(evaluationResultService.listResults(null, null, null, 0, 1,
                lecturerFour.getUser().getId()).totalElements()).isZero();
        assertThat(evaluationResultService.listResults(null, null, null, 0, 1,
                lecturerThree.getUser().getId()).totalElements()).isEqualTo(1);
        mvc.perform(get("/api/v1/results/" + finalized.id())
                .with(user(UserPrincipal.from(lecturerFour.getUser())))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/results/" + finalized.id())
                .with(user(UserPrincipal.from(leaderStudent.getUser())))).andExpect(status().isForbidden());
        assertThat(evaluationResultService.getResult(finalized.id(), lecturerThree.getUser().getId()).id())
                .isEqualTo(finalized.id());
        assertThat(evaluationResultService.listResults(null, null, null, 0, 10,
                lecturerOne.getUser().getId()).totalElements()).isZero();

        AcademicAppointment dean = new AcademicAppointment();
        dean.setLecturer(lecturerFour);
        dean.setFaculty(createFaculty("OTHER"));
        dean.setPositionCode(PositionCode.DEAN);
        dean.setStartsAt(nowUtc().minusDays(1));
        dean.setAssignedBy(admin);
        appointments.saveAndFlush(dean);
        assertThat(evaluationResultService.listResults(null, null, null, 0, 10,
                lecturerFour.getUser().getId()).totalElements()).isZero();
        dean.setFaculty(faculty);
        assertThat(evaluationResultService.listResults(null, null, null, 0, 10,
                lecturerFour.getUser().getId()).totalElements()).isEqualTo(1);
        dean.setEndsAt(nowUtc().minusSeconds(1));
        assertThat(evaluationResultService.listResults(null, null, null, 0, 10,
                lecturerFour.getUser().getId()).totalElements()).isZero();

        evaluationResultService.publish(finalized.id(), admin.getId());
        assertThat(evaluationResultService.getResult(finalized.id(), leaderStudent.getUser().getId()).id())
                .isEqualTo(finalized.id());
        mvc.perform(get("/api/v1/student/results")
                        .with(user(UserPrincipal.from(leaderStudent.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(finalized.id()))
                .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/student/results")
                        .with(user(UserPrincipal.from(thirdStudent.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        assertThatThrownBy(() -> evaluationResultService.getResult(finalized.id(), thirdStudent.getUser().getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(evaluationResultService.listResults(null, null, null, 0, 10,
                lecturerOne.getUser().getId()).totalElements()).isEqualTo(1);
    }

    @Test
    void studentsOnlyListRegistrationsForTheirOwnGroups(@Autowired MockMvc mvc) throws Exception {
        var project = createAssignedProject("student-registration-scope", leaderStudent, secondStudent);

        mvc.perform(get("/api/v1/topic-registrations/mine")
                        .with(user(UserPrincipal.from(leaderStudent.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].groupId").value(project.groupId()));
        mvc.perform(get("/api/v1/topic-registrations/mine")
                        .with(user(UserPrincipal.from(thirdStudent.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(get("/api/v1/topic-registrations")
                        .with(user(UserPrincipal.from(leaderStudent.getUser()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/topic-registrations/{id}", project.registrationId())
                        .with(user(UserPrincipal.from(leaderStudent.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(project.groupId()));
        mvc.perform(get("/api/v1/topic-registrations/{id}", project.registrationId())
                        .with(user(UserPrincipal.from(thirdStudent.getUser()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditReproducesGradeChangeListDisclosureToUnrelatedLecturer(@Autowired MockMvc mvc) throws Exception {
        var project = createAssignedProject("grade-change-disclosure", leaderStudent, secondStudent);
        var catalog = createLockedCatalog("disclosure", "Review", AssessmentComponentType.OTHER);
        Long evaluationId = evaluationService.assign(new CreateEvaluationRequest(project.id(), catalog.rubric().id(),
                lecturerThree.getId(), null, nowUtc().plusDays(1)), admin.getId());
        var sheet = evaluationService.saveSheet(evaluationId, new SaveSheetRequest(0, "Complete",
                scoreRequests(catalog.rubric(), new BigDecimal("6"), new BigDecimal("4")), true), lecturerThree.getUser().getId());
        Long change = gradeChangeService.requestChange(sheet.id(), new CreateGradeChangeRequest("Private correction reason"),
                lecturerThree.getUser().getId());
        // Audit reproducer: this unrelated lecturer must receive an empty list after the scoped-read fix.
        mvc.perform(get("/api/v1/grade-change-requests").param("sheetId", sheet.id().toString())
                        .with(user(UserPrincipal.from(lecturerFive.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(change))
                .andExpect(jsonPath("$.data.content[0].reason").value("Private correction reason"));
        mvc.perform(get("/api/v1/evaluation-assignments/{id}/score-sheet", evaluationId)
                        .with(user(UserPrincipal.from(lecturerFive.getUser()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/evaluation-assignments/{id}/score-sheet", evaluationId)
                        .with(user(UserPrincipal.from(thirdStudent.getUser()))))
                .andExpect(status().isForbidden());
    }

    private void submitFullScore(Long assignmentId, LockedCatalog catalog, Lecturer lecturer, Long defenseId) {
        Long id = evaluationService.assign(new CreateEvaluationRequest(assignmentId, catalog.rubric().id(),
                lecturer.getId(), defenseId, nowUtc().plusDays(1)), admin.getId());
        evaluationService.saveSheet(id, new SaveSheetRequest(0, "Complete",
                scoreRequests(catalog.rubric(), new BigDecimal("6"), new BigDecimal("4")), true), lecturer.getUser().getId());
    }

    private StudentGroupResponse createActiveGroup(String name, Student leader, Student member) {
        StudentGroupResponse group =
                groupService.create(
                        new CreateGroupRequest(period.getId(), name), leader.getUser().getId());
        return groupService.addMember(
                group.id(),
                new AddGroupMemberRequest(member.getStudentCode()),
                leader.getUser().getId());
    }

    private ProjectAssignmentResponse createAssignedProject(
            String topicCode, Student leader, Student member) {
        TopicResponse topic = createPublishedTopic(topicCode);
        if (System.getProperty("result.email.mysql.url") != null) {
            // Advance the fixture from proposal to registration without overlapping SQL time windows.
            period.setProposalEndAt(nowUtc().minusDays(1));
            period.setRegistrationStartAt(nowUtc().minusHours(1));
            period.setRegistrationEndAt(nowUtc().plusDays(2));
            registrationPeriodRepository.flush();
        }
        StudentGroupResponse group = createActiveGroup(unique("Nhóm " + topicCode), leader, member);
        TopicRegistrationResponse registration =
                registrationService.create(
                        new CreateTopicRegistrationRequest(
                                group.id(), topic.id(), "Nhóm phù hợp với đề tài."),
                        leader.getUser().getId());
        registration =
                registrationService.review(
                        registration.id(),
                        new ReviewTopicRegistrationRequest(
                                registration.version(), RegistrationStatus.APPROVED, null),
                        admin.getId());
        return projectAssignmentService.create(
                new CreateAssignmentRequest(registration.id()), admin.getId());
    }

    private LockedCatalog createLockedCatalog(
            String code,
            String name,
            AssessmentComponentType componentType) {
        AssessmentComponentResponse component =
                evaluationService.createComponent(
                        new CreateAssessmentComponentRequest(
                                period.getId(),
                                unique(code),
                                name,
                                componentType),
                        admin.getId());
        RubricResponse draftRubric =
                evaluationService.createRubric(
                        new CreateRubricRequest(
                                component.id(),
                                name + " rubric",
                                List.of(
                                        new CriterionRequest(
                                                "Nội dung",
                                                "Đánh giá nội dung",
                                                new BigDecimal("6.00"),
                                                1),
                                        new CriterionRequest(
                                                "Trình bày",
                                                "Đánh giá trình bày",
                                                new BigDecimal("4.00"),
                                                2))),
                        admin.getId());
        RubricResponse lockedRubric = evaluationService.lockRubric(draftRubric.id(), admin.getId());
        AssessmentComponentResponse lockedComponent =
                evaluationService.lockComponent(component.id(), admin.getId());
        return new LockedCatalog(lockedComponent, lockedRubric);
    }

    private List<ScoreRequest> scoreRequests(
            RubricResponse rubric, BigDecimal firstScore, BigDecimal secondScore) {
        return List.of(
                new ScoreRequest(rubric.criteria().get(0).id(), firstScore, "Tiêu chí 1"),
                new ScoreRequest(rubric.criteria().get(1).id(), secondScore, "Tiêu chí 2"));
    }

    private Faculty createFaculty(String baseCode) {
        Faculty entity = new Faculty();
        entity.setCode(unique(baseCode));
        entity.setName("Khoa " + baseCode);
        entity.setActive(true);
        return facultyRepository.save(entity);
    }

    private Department createDepartment(Faculty owner, String baseCode) {
        Department entity = new Department();
        entity.setFaculty(owner);
        entity.setCode(unique(baseCode));
        entity.setName("Bộ môn " + baseCode);
        entity.setActive(true);
        return departmentRepository.save(entity);
    }

    private Semester createSemester() {
        Semester entity = new Semester();
        entity.setAcademicYear("2026-2027");
        entity.setSemesterNo(1);
        entity.setStartsOn(LocalDate.of(2026, 9, 1));
        entity.setEndsOn(LocalDate.of(2027, 1, 31));
        return semesterRepository.save(entity);
    }

    private RegistrationPeriod createActivePeriod() {
        LocalDateTime now = nowUtc();
        RegistrationPeriod entity = new RegistrationPeriod();
        entity.setFaculty(faculty);
        entity.setSemester(semester);
        entity.setName("Đợt backend 2 " + unique("period"));
        entity.setProjectType(ProjectType.COURSE);
        entity.setProposalStartAt(now.minusDays(2));
        entity.setProposalEndAt(now.plusDays(2));
        entity.setRegistrationStartAt(now.minusDays(2));
        entity.setRegistrationEndAt(now.plusDays(2));
        entity.setReviewDeadlineAt(now.plusDays(7));
        entity.setDefenseDate(now.toLocalDate().plusDays(30));
        entity.setStatus(RegistrationPeriodStatus.ACTIVE);
        entity.setCreatedBy(admin);
        entity.setCreatedAt(now.minusHours(3));
        if (System.getProperty("result.email.mysql.url") != null) {
            entity.setRegistrationStartAt(now.plusDays(3));
            entity.setRegistrationEndAt(now.plusDays(5));
            entity.setReviewDeadlineAt(null);
            entity.setDefenseDate(null);
        }
        return registrationPeriodRepository.save(entity);
    }

    private Lecturer createLecturer(String baseCode, String fullName) {
        User user = createUser(baseCode.toLowerCase(), fullName, Set.of(RoleCode.LECTURER));
        Lecturer entity = new Lecturer();
        entity.setUser(user);
        entity.setDepartment(department);
        entity.setLecturerCode(unique(baseCode));
        entity.setAcademicDegree("ThS");
        entity.setSpecialization("Spring Boot");
        return lecturerRepository.save(entity);
    }

    private Student createStudent(String baseCode, String fullName) {
        User user = createUser(baseCode.toLowerCase(), fullName, Set.of(RoleCode.STUDENT));
        Student entity = new Student();
        entity.setUser(user);
        entity.setDepartment(department);
        entity.setStudentCode(unique(baseCode));
        entity.setClassName("D21CQCN01");
        entity.setCohort("K21");
        return studentRepository.save(entity);
    }

    private User createUser(String emailPrefix, String fullName, Set<RoleCode> roleCodes) {
        LocalDateTime now = nowUtc();
        User entity = new User();
        entity.setEmail(unique(emailPrefix) + "@test.local");
        entity.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        entity.setFullName(fullName);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.replaceRoles(resolveRoles(roleCodes), now);
        return userRepository.save(entity);
    }

    private Set<Role> resolveRoles(Set<RoleCode> roleCodes) {
        return new LinkedHashSet<>(roleRepository.findAllByCodeIn(roleCodes));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(clock);
    }

    private String unique(String base) {
        return base + "-" + Long.toUnsignedString(System.nanoTime(), 36);
    }

    private record LockedCatalog(AssessmentComponentResponse component, RubricResponse rubric) {}
}
