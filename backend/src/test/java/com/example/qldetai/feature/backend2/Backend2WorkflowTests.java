package com.example.qldetai.feature.backend2;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.academic.Department;
import com.example.qldetai.feature.academic.DepartmentRepository;
import com.example.qldetai.feature.academic.Faculty;
import com.example.qldetai.feature.academic.FacultyRepository;
import com.example.qldetai.feature.academic.ProjectType;
import com.example.qldetai.feature.academic.RegistrationPeriod;
import com.example.qldetai.feature.academic.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.Semester;
import com.example.qldetai.feature.academic.SemesterRepository;
import com.example.qldetai.feature.groupregistration.GroupMember;
import com.example.qldetai.feature.groupregistration.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.AddGroupMemberRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.CreateTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.ReviewTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.StudentGroupResponse;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.TopicRegistrationResponse;
import com.example.qldetai.feature.groupregistration.GroupService;
import com.example.qldetai.feature.groupregistration.GroupStatus;
import com.example.qldetai.feature.groupregistration.RegistrationService;
import com.example.qldetai.feature.groupregistration.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.TopicRegistration;
import com.example.qldetai.feature.groupregistration.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.Lecturer;
import com.example.qldetai.feature.identity.LecturerRepository;
import com.example.qldetai.feature.identity.Role;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.RoleRepository;
import com.example.qldetai.feature.identity.Student;
import com.example.qldetai.feature.identity.StudentRepository;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.identity.UserStatus;
import com.example.qldetai.feature.evaluation.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.CommitteeRole;
import com.example.qldetai.feature.evaluation.EvaluationAdministrationService;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.AddCommitteeMemberRequest;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.CreateCommitteeRequest;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.CreateDefenseRequest;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.CreateGradeChangeRequest;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.DefenseSessionResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.GradeChangeRequestResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.ResultResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.ReviewGradeChangeRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.AssessmentComponentResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateAssessmentComponentRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateEvaluationRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateRubricRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CriterionRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.EvaluationAssignmentResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.RubricResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.SaveSheetRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.ScoreRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.ScoreSheetResponse;
import com.example.qldetai.feature.evaluation.EvaluationService;
import com.example.qldetai.feature.evaluation.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.ScoreSheetStatus;
import com.example.qldetai.feature.project.AssignmentStatus;
import com.example.qldetai.feature.project.ProjectAssignmentRepository;
import com.example.qldetai.feature.project.ProjectAssignmentService;
import com.example.qldetai.feature.project.ProjectDtos.CreateAssignmentRequest;
import com.example.qldetai.feature.project.ProjectDtos.ProjectAssignmentResponse;
import com.example.qldetai.feature.project.ProjectDtos.UpdateAssignmentStatusRequest;
import com.example.qldetai.feature.topic.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.TopicDtos.PublishTopicRequest;
import com.example.qldetai.feature.topic.TopicDtos.ReviewTopicRequest;
import com.example.qldetai.feature.topic.TopicDtos.TopicResponse;
import com.example.qldetai.feature.topic.TopicReviewDecision;
import com.example.qldetai.feature.topic.TopicService;
import com.example.qldetai.feature.topic.TopicStatus;
import com.example.qldetai.feature.topic.VisibilityType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class Backend2WorkflowTests {

    private final TopicService topicService;
    private final GroupService groupService;
    private final RegistrationService registrationService;
    private final ProjectAssignmentService projectAssignmentService;
    private final EvaluationService evaluationService;
    private final EvaluationAdministrationService evaluationAdministrationService;
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

    Backend2WorkflowTests(
            TopicService topicService,
            GroupService groupService,
            RegistrationService registrationService,
            ProjectAssignmentService projectAssignmentService,
            EvaluationService evaluationService,
            EvaluationAdministrationService evaluationAdministrationService,
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
        this.evaluationAdministrationService = evaluationAdministrationService;
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
        TopicResponse created = topicService.create(
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

        TopicResponse reviewed = topicService.review(
                created.id(),
                new ReviewTopicRequest(created.version(), TopicReviewDecision.APPROVED, "Đề tài đạt yêu cầu."),
                admin.getId());

        assertThat(reviewed.status()).isEqualTo(TopicStatus.APPROVED);
        assertThat(reviewed.latestReview()).isNotNull();
        assertThat(reviewed.latestReview().decision()).isEqualTo(TopicReviewDecision.APPROVED);

        TopicResponse assigned = topicService.assignSupervisors(
                reviewed.id(),
                new AssignSupervisorsRequest(reviewed.version(), lecturerOne.getId(), lecturerTwo.getId()),
                admin.getId());

        assertThat(assigned.supervisors()).hasSize(2);
        assertThat(assigned.supervisors().getFirst().slotNo()).isEqualTo(1);
        assertThat(assigned.supervisors().get(1).slotNo()).isEqualTo(2);

        TopicResponse published = topicService.publish(
                assigned.id(),
                new PublishTopicRequest(assigned.version()),
                admin.getId());

        assertThat(published.status()).isEqualTo(TopicStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.supervisors()).hasSize(2);
    }

    @Test
    void groupMemberSlotIsReusedAfterRemovingAMember() {
        StudentGroupResponse created = groupService.create(
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
        groupService.removeMember(created.id(), secondStudent.getId(), leaderStudent.getUser().getId());

        StudentGroupResponse updated = groupService.addMember(
                created.id(),
                new AddGroupMemberRequest(fourthStudent.getStudentCode()),
                leaderStudent.getUser().getId());

        List<GroupMember> activeMembers = groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(created.id());
        assertThat(updated.status()).isEqualTo(GroupStatus.ACTIVE);
        assertThat(activeMembers).hasSize(3);
        assertThat(activeMembers)
                .extracting(GroupMember::getSlotNo)
                .containsExactly(1, 2, 3);
        assertThat(activeMembers)
                .extracting(member -> member.getStudent().getStudentCode())
                .containsExactly(leaderStudent.getStudentCode(), fourthStudent.getStudentCode(), thirdStudent.getStudentCode());
    }

    @Test
    void topicRegistrationCanBeSubmittedAndApproved() {
        TopicResponse topic = createPublishedTopic("dt-reg-01");
        StudentGroupResponse group = createActiveGroup("Nhóm đăng ký", leaderStudent, secondStudent);

        TopicRegistrationResponse created = registrationService.create(
                new CreateTopicRegistrationRequest(group.id(), topic.id(), "Nhóm phù hợp với đề tài này."),
                leaderStudent.getUser().getId());

        assertThat(created.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(created.topicCode()).isEqualTo(topic.code());

        TopicRegistrationResponse reviewed = registrationService.review(
                created.id(),
                new ReviewTopicRegistrationRequest(created.version(), RegistrationStatus.APPROVED, null),
                admin.getId());

        assertThat(reviewed.status()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(reviewed.reviewedBy()).isEqualTo(admin.getId());
        assertThat(reviewed.decisionReason()).isNull();
    }

    @Test
    void assignmentCreationRejectsOtherPendingRegistrationAndCompletionClosesGroup() {
        TopicResponse topic = createPublishedTopic("dt-assign-01");
        StudentGroupResponse firstGroup = createActiveGroup("Nhóm A", leaderStudent, secondStudent);
        StudentGroupResponse secondGroup = createActiveGroup("Nhóm B", thirdStudent, fourthStudent);

        TopicRegistrationResponse approvedRegistration = registrationService.create(
                new CreateTopicRegistrationRequest(firstGroup.id(), topic.id(), "Nhóm A đăng ký."),
                leaderStudent.getUser().getId());
        approvedRegistration = registrationService.review(
                approvedRegistration.id(),
                new ReviewTopicRegistrationRequest(approvedRegistration.version(), RegistrationStatus.APPROVED, null),
                admin.getId());

        TopicRegistrationResponse pendingRegistration = registrationService.create(
                new CreateTopicRegistrationRequest(secondGroup.id(), topic.id(), "Nhóm B đăng ký."),
                thirdStudent.getUser().getId());

        ProjectAssignmentResponse created = projectAssignmentService.create(
                new CreateAssignmentRequest(approvedRegistration.id()),
                admin.getId());

        assertThat(created.status()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(projectAssignmentRepository.existsByGroupId(firstGroup.id())).isTrue();
        assertThat(studentGroupRepository.findById(firstGroup.id()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.LOCKED);

        TopicRegistration rejectedRegistration =
                topicRegistrationRepository.findById(pendingRegistration.id()).orElseThrow();
        assertThat(rejectedRegistration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);

        ProjectAssignmentResponse completed = projectAssignmentService.updateStatus(
                created.id(),
                new UpdateAssignmentStatusRequest(created.version(), AssignmentStatus.COMPLETED),
                admin.getId());

        assertThat(completed.status()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(studentGroupRepository.findById(firstGroup.id()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.CLOSED);
    }

    @Test
    void assessmentCatalogCanBeLockedBeforeEvaluatorAssignment() {
        LockedCatalog catalog = createLockedCatalog(
                "rv-01",
                "Chấm phản biện",
                AssessmentComponentType.REVIEW,
                1);

        assertThat(catalog.component().componentType()).isEqualTo(AssessmentComponentType.REVIEW);
        assertThat(catalog.component().status()).isEqualTo("LOCKED");
        assertThat(catalog.rubric().status()).isEqualTo("LOCKED");
        assertThat(catalog.rubric().criteria())
                .extracting(criterion -> criterion.maxScore())
                .containsExactly(new BigDecimal("6.00"), new BigDecimal("4.00"));
    }

    @Test
    void committeeEvaluationAppearsInAssignmentAndDefenseReads() {
        ProjectAssignmentResponse assignment = createAssignedProject("dt-defense-01", leaderStudent, secondStudent);
        LockedCatalog catalog = createLockedCatalog(
                "hd-01",
                "Chấm hội đồng",
                AssessmentComponentType.COMMITTEE,
                3);

        Long committeeId = evaluationAdministrationService.createCommittee(
                new CreateCommitteeRequest(period.getId(), "Hội đồng demo"),
                admin.getId());
        evaluationAdministrationService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerThree.getId(), 1, CommitteeRole.CHAIR),
                admin.getId());
        evaluationAdministrationService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerFour.getId(), 2, CommitteeRole.SECRETARY),
                admin.getId());
        evaluationAdministrationService.addMember(
                committeeId,
                new AddCommitteeMemberRequest(lecturerFive.getId(), 3, CommitteeRole.MEMBER),
                admin.getId());

        LocalDateTime startsAt = nowUtc().plusDays(5);
        Long defenseId = evaluationAdministrationService.schedule(
                new CreateDefenseRequest(
                        assignment.id(),
                        committeeId,
                        "Phòng A101",
                        startsAt,
                        startsAt.plusHours(1)),
                admin.getId());

        Long evaluationId = evaluationService.assign(
                new CreateEvaluationRequest(
                        assignment.id(),
                        catalog.rubric().id(),
                        lecturerThree.getId(),
                        defenseId,
                        nowUtc().plusDays(7)),
                admin.getId());

        PageResponse<EvaluationCommitteeResponse> committees =
                evaluationAdministrationService.listCommittees(period.getId(), 0, 10);
        PageResponse<DefenseSessionResponse> defenses =
                evaluationAdministrationService.listDefenses(period.getId(), committeeId, 0, 10);
        PageResponse<EvaluationAssignmentResponse> evaluations = evaluationService.listAssignments(
                period.getId(),
                assignment.id(),
                lecturerThree.getId(),
                null,
                0,
                10);

        assertThat(committeeId).isPositive();
        assertThat(defenseId).isPositive();
        assertThat(evaluationId).isPositive();
        assertThat(committees.content()).singleElement().satisfies(committee -> {
            assertThat(committee.status().name()).isEqualTo("CONFIRMED");
            assertThat(committee.members()).hasSize(3);
        });
        assertThat(defenses.content()).singleElement().satisfies(defense -> {
            assertThat(defense.id()).isEqualTo(defenseId);
            assertThat(defense.assignmentId()).isEqualTo(assignment.id());
            assertThat(defense.committeeId()).isEqualTo(committeeId);
        });
        assertThat(evaluations.content()).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.id()).isEqualTo(evaluationId);
            assertThat(evaluation.defenseId()).isEqualTo(defenseId);
            assertThat(evaluation.evaluatorRole()).isEqualTo("COMMITTEE_MEMBER");
            assertThat(evaluation.lecturerId()).isEqualTo(lecturerThree.getId());
        });
    }

    @Test
    void approvedGradeChangeBecomesAppliedAfterEvaluatorResubmits() {
        ProjectAssignmentResponse assignment = createAssignedProject("dt-grade-change-01", leaderStudent, secondStudent);
        LockedCatalog catalog = createLockedCatalog(
                "pb-01",
                "Chấm phản biện 1",
                AssessmentComponentType.REVIEW,
                1);

        Long evaluationId = evaluationService.assign(
                new CreateEvaluationRequest(
                        assignment.id(),
                        catalog.rubric().id(),
                        lecturerThree.getId(),
                        null,
                        nowUtc().plusDays(4)),
                admin.getId());

        ScoreSheetResponse submittedSheet = evaluationService.saveSheet(
                evaluationId,
                new SaveSheetRequest(
                        0,
                        "Bản chấm đầu tiên",
                        scoreRequests(catalog.rubric(), new BigDecimal("6.00"), new BigDecimal("4.00")),
                        true),
                lecturerThree.getUser().getId());

        ResultResponse finalized = evaluationAdministrationService.finalizeResult(assignment.id(), admin.getId());
        Long changeId = evaluationAdministrationService.requestChange(
                submittedSheet.id(),
                new CreateGradeChangeRequest("Cần chỉnh lại nhận xét và điểm."),
                lecturerThree.getUser().getId());
        evaluationAdministrationService.reviewChange(
                changeId,
                new ReviewGradeChangeRequest(true, "Cho phép sửa lại phiếu chấm."),
                admin.getId());

        ScoreSheetResponse reopenedSheet = evaluationService.mySheet(evaluationId, lecturerThree.getUser().getId());
        PageResponse<GradeChangeRequestResponse> approvedChanges = evaluationAdministrationService.listChanges(
                GradeChangeStatus.APPROVED,
                submittedSheet.id(),
                0,
                10,
                admin.getId());

        ScoreSheetResponse resubmittedSheet = evaluationService.saveSheet(
                evaluationId,
                new SaveSheetRequest(
                        reopenedSheet.version(),
                        "Bản chấm đã chỉnh sửa",
                        scoreRequests(catalog.rubric(), new BigDecimal("5.50"), new BigDecimal("4.00")),
                        true),
                lecturerThree.getUser().getId());

        PageResponse<GradeChangeRequestResponse> allChanges = evaluationAdministrationService.listChanges(
                null,
                submittedSheet.id(),
                0,
                10,
                admin.getId());

        assertThat(finalized.status().name()).isEqualTo("FINALIZED");
        assertThat(reopenedSheet.status()).isEqualTo(ScoreSheetStatus.DRAFT);
        assertThat(approvedChanges.content()).singleElement().satisfies(change -> {
            assertThat(change.id()).isEqualTo(changeId);
            assertThat(change.status()).isEqualTo(GradeChangeStatus.APPROVED);
            assertThat(change.appliedAt()).isNull();
        });
        assertThat(resubmittedSheet.status()).isEqualTo(ScoreSheetStatus.SUBMITTED);
        assertThat(allChanges.content()).singleElement().satisfies(change -> {
            assertThat(change.id()).isEqualTo(changeId);
            assertThat(change.status()).isEqualTo(GradeChangeStatus.APPLIED);
            assertThat(change.appliedAt()).isNotNull();
        });
    }

    private TopicResponse createPublishedTopic(String code) {
        TopicResponse created = topicService.create(
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
        TopicResponse reviewed = topicService.review(
                created.id(),
                new ReviewTopicRequest(created.version(), TopicReviewDecision.APPROVED, "Đạt."),
                admin.getId());
        TopicResponse assigned = topicService.assignSupervisors(
                reviewed.id(),
                new AssignSupervisorsRequest(reviewed.version(), lecturerOne.getId(), lecturerTwo.getId()),
                admin.getId());
        return topicService.publish(
                assigned.id(),
                new PublishTopicRequest(assigned.version()),
                admin.getId());
    }

    private StudentGroupResponse createActiveGroup(String name, Student leader, Student member) {
        StudentGroupResponse group = groupService.create(
                new CreateGroupRequest(period.getId(), name),
                leader.getUser().getId());
        return groupService.addMember(
                group.id(),
                new AddGroupMemberRequest(member.getStudentCode()),
                leader.getUser().getId());
    }

    private ProjectAssignmentResponse createAssignedProject(String topicCode, Student leader, Student member) {
        TopicResponse topic = createPublishedTopic(topicCode);
        StudentGroupResponse group = createActiveGroup(unique("Nhóm " + topicCode), leader, member);
        TopicRegistrationResponse registration = registrationService.create(
                new CreateTopicRegistrationRequest(group.id(), topic.id(), "Nhóm phù hợp với đề tài."),
                leader.getUser().getId());
        registration = registrationService.review(
                registration.id(),
                new ReviewTopicRegistrationRequest(registration.version(), RegistrationStatus.APPROVED, null),
                admin.getId());
        return projectAssignmentService.create(
                new CreateAssignmentRequest(registration.id()),
                admin.getId());
    }

    private LockedCatalog createLockedCatalog(
            String code,
            String name,
            AssessmentComponentType componentType,
            int expectedEvaluators) {
        AssessmentComponentResponse component = evaluationService.createComponent(
                new CreateAssessmentComponentRequest(
                        period.getId(),
                        unique(code),
                        name,
                        componentType,
                        expectedEvaluators),
                admin.getId());
        RubricResponse draftRubric = evaluationService.createRubric(
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
        AssessmentComponentResponse lockedComponent = evaluationService.lockComponent(component.id(), admin.getId());
        return new LockedCatalog(lockedComponent, lockedRubric);
    }

    private List<ScoreRequest> scoreRequests(RubricResponse rubric, BigDecimal firstScore, BigDecimal secondScore) {
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
        return base + "-" + System.nanoTime();
    }

    private record LockedCatalog(AssessmentComponentResponse component, RubricResponse rubric) {
    }
}
