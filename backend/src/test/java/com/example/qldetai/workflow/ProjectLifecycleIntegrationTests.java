package com.example.qldetai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class ProjectLifecycleIntegrationTests {

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
                createLockedCatalog("rv-01", "Chấm phản biện", AssessmentComponentType.REVIEW, 1);

        assertThat(catalog.component().componentType()).isEqualTo(AssessmentComponentType.REVIEW);
        assertThat(catalog.component().status()).isEqualTo("LOCKED");
        assertThat(catalog.rubric().status()).isEqualTo("LOCKED");
        assertThat(catalog.rubric().criteria())
                .extracting(criterion -> criterion.maxScore())
                .containsExactly(new BigDecimal("6.00"), new BigDecimal("4.00"));
    }

    @Test
    void committeeEvaluationAppearsInAssignmentAndDefenseReads() {
        ProjectAssignmentResponse assignment =
                createAssignedProject("dt-defense-01", leaderStudent, secondStudent);
        LockedCatalog catalog =
                createLockedCatalog("hd-01", "Chấm hội đồng", AssessmentComponentType.COMMITTEE, 3);

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
                        "review-gate-01", "Phản biện bắt buộc", AssessmentComponentType.REVIEW, 1);
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
    }

    @Test
    void gradeChangeCannotReopenAScoreSheetUsedByAFinalizedResult() {
        ProjectAssignmentResponse assignment =
                createAssignedProject("dt-grade-change-01", leaderStudent, secondStudent);
        LockedCatalog catalog =
                createLockedCatalog("pb-01", "Chấm phản biện 1", AssessmentComponentType.REVIEW, 1);

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
                        AssessmentComponentType.OTHER,
                        1);
        LockedCatalog twoEvaluatorComponent =
                createLockedCatalog(
                        "review-formula-01",
                        "Điểm thành phần hai người chấm",
                        AssessmentComponentType.REVIEW,
                        2);

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
                        "AUTH", "Authorization rubric", AssessmentComponentType.REVIEW, 1);
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
            mvc.perform(get("/api/v1/" + resource).with(user(principal)))
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
            AssessmentComponentType componentType,
            int expectedEvaluators) {
        AssessmentComponentResponse component =
                evaluationService.createComponent(
                        new CreateAssessmentComponentRequest(
                                period.getId(),
                                unique(code),
                                name,
                                componentType,
                                expectedEvaluators),
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
