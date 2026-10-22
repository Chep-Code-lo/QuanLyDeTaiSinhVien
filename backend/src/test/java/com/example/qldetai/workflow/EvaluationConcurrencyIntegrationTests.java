package com.example.qldetai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.qldetai.feature.communication.email.*;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.AddCommitteeMemberRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateCommitteeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateGradeChangeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ReviewGradeChangeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.AssessmentComponentResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.BatchEvaluationRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.BatchEvaluationResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateAssessmentComponentRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateEvaluationRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateRubricRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CriterionRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.RubricResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.SaveSheetRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ScoreRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ScoreSheetResponse;
import com.example.qldetai.feature.evaluation.service.EvaluationCommitteeService;
import com.example.qldetai.feature.evaluation.service.EvaluationResultService;
import com.example.qldetai.feature.evaluation.service.EvaluationService;
import com.example.qldetai.feature.evaluation.service.GradeChangeService;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.AddGroupMemberRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.ReviewTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.StudentGroupResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.TopicRegistrationResponse;
import com.example.qldetai.feature.groupregistration.service.GroupService;
import com.example.qldetai.feature.groupregistration.service.RegistrationService;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.dto.ProjectDtos.CreateAssignmentRequest;
import com.example.qldetai.feature.project.dto.ProjectDtos.ProjectAssignmentResponse;
import com.example.qldetai.feature.project.service.ProjectAssignmentService;
import com.example.qldetai.feature.topic.domain.TopicReviewDecision;
import com.example.qldetai.feature.topic.domain.VisibilityType;
import com.example.qldetai.feature.topic.dto.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.PublishTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.ReviewTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.TopicResponse;
import com.example.qldetai.feature.topic.service.TopicService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:evaluation-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class EvaluationConcurrencyIntegrationTests {
    @DynamicPropertySource
    static void isolatedMysql(DynamicPropertyRegistry properties) {
        String url = System.getProperty("race.test.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:")
                    || !url.contains("/ql_de_tai_sinh_vien?")) {
                throw new IllegalArgumentException(
                        "Concurrency tests require a disposable local database.");
            }
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add(
                    "spring.datasource.username",
                    () -> System.getProperty("race.test.mysql.user", "root"));
            properties.add(
                    "spring.datasource.password",
                    () -> System.getProperty("race.test.mysql.password"));
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
            properties.add("spring.sql.init.mode", () -> "never");
        }
    }

    @Autowired private TopicService topicService;
    @Autowired private GroupService groupService;
    @Autowired private RegistrationService registrationService;
    @Autowired private ProjectAssignmentService projectAssignmentService;
    @Autowired private EvaluationService evaluationService;
    @Autowired private EvaluationResultService evaluationResultService;
    @Autowired private GradeChangeService gradeChangeService;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private RegistrationPeriodRepository registrationPeriodRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;

    @Autowired
    private com.example.qldetai.feature.evaluation.service.EvaluationAssignmentBatchService batches;

    @Autowired
    private com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository
            evaluations;

    @Autowired
    private com.example.qldetai.feature.evaluation.repository.GradeChangeRequestRepository changes;

    @Autowired
    private com.example.qldetai.feature.evaluation.repository.ResultVersionRepository results;

    @Autowired
    private com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository sheets;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
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
        tx().executeWithoutResult(
                        status -> {
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
                        });
    }

    private org.springframework.transaction.support.TransactionTemplate tx() {
        return new org.springframework.transaction.support.TransactionTemplate(transactions);
    }

    private record Fixture(
            Long projectId, LockedCatalog catalog, Long evaluationId, Long sheetId, int version) {}

    private Fixture fixture(boolean submitted, AssessmentComponentType type) {
        return tx().execute(
                        status -> {
                            var project =
                                    createAssignedProject(
                                            unique("race"), leaderStudent, secondStudent);
                            var catalog = createLockedCatalog("race", "Race", type);
                            Long evaluation =
                                    evaluationService.assign(
                                            new CreateEvaluationRequest(
                                                    project.id(),
                                                    catalog.rubric().id(),
                                                    lecturerThree.getId(),
                                                    null,
                                                    nowUtc().plusDays(1)),
                                            admin.getId());
                            var sheet =
                                    evaluationService.saveSheet(
                                            evaluation,
                                            new SaveSheetRequest(
                                                    0,
                                                    "Initial",
                                                    scoreRequests(
                                                            catalog.rubric(),
                                                            new BigDecimal("6"),
                                                            new BigDecimal("4")),
                                                    submitted),
                                            lecturerThree.getUser().getId());
                            return new Fixture(
                                    project.id(), catalog, evaluation, sheet.id(), sheet.version());
                        });
    }

    private record Outcome(Object value, RuntimeException error) {}

    private List<Outcome> race(
            java.util.concurrent.Callable<?> left, java.util.concurrent.Callable<?> right)
            throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Outcome>>();
            for (var task : List.of(left, right)) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    assertThat(
                                                    start.await(
                                                            10,
                                                            java.util.concurrent.TimeUnit.SECONDS))
                                            .isTrue();
                                    try {
                                        return new Outcome(task.call(), null);
                                    } catch (RuntimeException exception) {
                                        return new Outcome(null, exception);
                                    }
                                }));
            }
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    futures.get(0).get(30, java.util.concurrent.TimeUnit.SECONDS),
                    futures.get(1).get(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private void oneWinner(List<Outcome> outcomes) {
        assertThat(outcomes.stream().filter(outcome -> outcome.error() == null).count())
                .isEqualTo(1);
        assertThat(
                        outcomes.stream()
                                .filter(outcome -> outcome.error() != null)
                                .findFirst()
                                .orElseThrow()
                                .error())
                .isInstanceOfAny(
                        BusinessRuleException.class,
                        com.example.qldetai.exception.ConflictException.class,
                        org.springframework.security.access.AccessDeniedException.class);
    }

    private ScoreSheetResponse save(
            Fixture fixture,
            int version,
            String comment,
            String first,
            String second,
            boolean submit,
            Long actor) {
        return evaluationService.saveSheet(
                fixture.evaluationId(),
                new SaveSheetRequest(
                        version,
                        comment,
                        scoreRequests(
                                fixture.catalog().rubric(),
                                new BigDecimal(first),
                                new BigDecimal(second)),
                        submit),
                actor);
    }

    @Test
    void concurrentSubmissionsOfOneSheetHaveOneWinner() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.OTHER);
        oneWinner(
                race(
                        () ->
                                save(
                                        fixture,
                                        fixture.version(),
                                        "First",
                                        "5",
                                        "3",
                                        true,
                                        lecturerThree.getUser().getId()),
                        () ->
                                save(
                                        fixture,
                                        fixture.version(),
                                        "Second",
                                        "4",
                                        "2",
                                        true,
                                        lecturerThree.getUser().getId())));
        assertThat(sheets.findById(fixture.sheetId()).orElseThrow().getStatus())
                .isEqualTo(ScoreSheetStatus.SUBMITTED);
    }

    @Test
    void unrelatedLecturerCannotSubmitAnotherLecturersSheetDuringSubmission() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.OTHER);
        var outcomes =
                race(
                        () ->
                                save(
                                        fixture,
                                        fixture.version(),
                                        "Owner",
                                        "6",
                                        "4",
                                        true,
                                        lecturerThree.getUser().getId()),
                        () ->
                                save(
                                        fixture,
                                        fixture.version(),
                                        "Unrelated",
                                        "4",
                                        "2",
                                        true,
                                        lecturerFour.getUser().getId()));
        assertThat(outcomes.get(0).error()).isNull();
        assertThat(outcomes.get(1).error())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(sheets.findById(fixture.sheetId()).orElseThrow().getOverallComment())
                .isEqualTo("Owner");
    }

    @Test
    void concurrentEditsDoNotMixDetailsOrLoseTheVersionCheck() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.OTHER);
        oneWinner(
                race(
                        () ->
                                save(
                                        fixture,
                                        fixture.version(),
                                        "First",
                                        "5",
                                        "3",
                                        false,
                                        lecturerThree.getUser().getId()),
                        () ->
                                save(
                                        fixture,
                                        fixture.version(),
                                        "Second",
                                        "4",
                                        "2",
                                        false,
                                        lecturerThree.getUser().getId())));
        var saved =
                evaluationService.mySheet(fixture.evaluationId(), lecturerThree.getUser().getId());
        assertThat(saved.version()).isGreaterThan(fixture.version());
        assertThat(saved.totalScore())
                .isEqualByComparingTo(saved.overallComment().equals("First") ? "8" : "6");
        assertThat(saved.scores()).hasSize(2);
    }

    @Test
    void concurrentApprovalsReviewTheRequestOnce() throws Exception {
        var fixture = fixture(true, AssessmentComponentType.OTHER);
        Long change =
                gradeChangeService.requestChange(
                        fixture.sheetId(),
                        new CreateGradeChangeRequest("Correction"),
                        lecturerThree.getUser().getId());
        var review = new ReviewGradeChangeRequest(true, "Approved");
        oneWinner(
                race(
                        () -> {
                            gradeChangeService.reviewChange(change, review, admin.getId());
                            return true;
                        },
                        () -> {
                            gradeChangeService.reviewChange(change, review, admin.getId());
                            return true;
                        }));
        assertThat(changes.findById(change).orElseThrow().getStatus())
                .isEqualTo(GradeChangeStatus.APPROVED);
        assertThat(sheets.findById(fixture.sheetId()).orElseThrow().getStatus())
                .isEqualTo(ScoreSheetStatus.DRAFT);
    }

    @Test
    void concurrentFinalizationLocksOneSheetAndCreatesOneSnapshot() throws Exception {
        var fixture = fixture(true, AssessmentComponentType.OTHER);
        oneWinner(
                race(
                        () ->
                                evaluationResultService.finalizeResult(
                                        fixture.projectId(), admin.getId()),
                        () ->
                                evaluationResultService.finalizeResult(
                                        fixture.projectId(), admin.getId())));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from result_versions where assignment_id = ?",
                                Long.class,
                                fixture.projectId()))
                .isEqualTo(1);
        assertThat(sheets.findById(fixture.sheetId()).orElseThrow().getStatus())
                .isEqualTo(ScoreSheetStatus.LOCKED);
    }

    @Test
    void concurrentPublicationProducesOnePublishedResultAndOneNotificationPerMember()
            throws Exception {
        var fixture = fixture(true, AssessmentComponentType.OTHER);
        var result = evaluationResultService.finalizeResult(fixture.projectId(), admin.getId());
        oneWinner(
                race(
                        () -> evaluationResultService.publish(result.id(), admin.getId()),
                        () -> evaluationResultService.publish(result.id(), admin.getId())));
        assertThat(results.findById(result.id()).orElseThrow().getStatus().name())
                .isEqualTo("PUBLISHED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notifications where event_key = ?",
                                Long.class,
                                "result-published-" + result.id()))
                .isEqualTo(2);
    }

    @Test
    void publicationRacingWithEditingCannotChangeCapturedScores() throws Exception {
        var fixture = fixture(true, AssessmentComponentType.OTHER);
        var result = evaluationResultService.finalizeResult(fixture.projectId(), admin.getId());
        var outcomes =
                race(
                        () -> evaluationResultService.publish(result.id(), admin.getId()),
                        () ->
                                save(
                                        fixture,
                                        fixture.version(),
                                        "Tampered",
                                        "1",
                                        "1",
                                        false,
                                        lecturerThree.getUser().getId()));
        assertThat(outcomes.get(0).error()).isNull();
        assertThat(outcomes.get(1).error()).isInstanceOf(BusinessRuleException.class);
        assertThat(evaluationResultService.getResult(result.id(), admin.getId()).finalScore())
                .isEqualByComparingTo("10");
        assertThat(sheets.findById(fixture.sheetId()).orElseThrow().getStatus())
                .isEqualTo(ScoreSheetStatus.LOCKED);
    }

    private CreateEvaluationRequest reviewer(Fixture fixture, Lecturer lecturer) {
        return new CreateEvaluationRequest(
                fixture.projectId(),
                fixture.catalog().rubric().id(),
                lecturer.getId(),
                null,
                nowUtc().plusDays(1));
    }

    @Test
    void identicalConcurrentBatchesAreIdempotent() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.REVIEW);
        var batch = new BatchEvaluationRequest(List.of(reviewer(fixture, lecturerFour)));
        var outcomes =
                race(
                        () -> batches.assign(batch, admin.getId()),
                        () -> batches.assign(batch, admin.getId()));
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
        assertThat(
                        outcomes.stream()
                                .map(outcome -> (BatchEvaluationResponse) outcome.value())
                                .mapToInt(BatchEvaluationResponse::assignedCount)
                                .sum())
                .isEqualTo(1);
        assertThat(
                        evaluations.findByAssignmentIdAndStatus(
                                fixture.projectId(), EvaluationStatus.ACTIVE))
                .hasSize(2);
    }

    @Test
    void batchAndSingleAssignmentCannotDuplicateAReviewer() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.REVIEW);
        var request = reviewer(fixture, lecturerFour);
        var outcomes =
                race(
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(List.of(request, request)),
                                        admin.getId()),
                        () -> evaluationService.assign(request, admin.getId()));
        assertThat(outcomes.get(0).error()).isNull();
        if (outcomes.get(1).error() != null) {
            assertThat(outcomes.get(1).error())
                    .isInstanceOf(com.example.qldetai.exception.ConflictException.class);
        }
        assertThat(
                        evaluations.findByAssignmentIdAndStatus(
                                fixture.projectId(), EvaluationStatus.ACTIVE))
                .hasSize(2);
    }

    @Test
    void oversizedBatchRacingWithValidBatchCannotPartiallyAssign() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.REVIEW);
        var request = reviewer(fixture, lecturerFour);
        var outcomes =
                race(
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(
                                                java.util.Collections.nCopies(101, request)),
                                        admin.getId()),
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(List.of(request)),
                                        admin.getId()));
        assertThat(outcomes.get(0).error()).isInstanceOf(BusinessRuleException.class);
        assertThat(outcomes.get(1).error()).isNull();
        assertThat(
                        evaluations.findByAssignmentIdAndStatus(
                                fixture.projectId(), EvaluationStatus.ACTIVE))
                .hasSize(2);
    }

    @Test
    void reviewerCountsAreDerivedFromActiveAssignmentsRatherThanTheLegacyColumn() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.REVIEW);
        var outcomes =
                race(
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(
                                                List.of(reviewer(fixture, lecturerFour))),
                                        admin.getId()),
                        () ->
                                evaluationService.assign(
                                        reviewer(fixture, lecturerFive), admin.getId()));
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
        assertThat(
                        evaluations.countByAssignmentIdAndComponentIdAndStatus(
                                fixture.projectId(),
                                fixture.catalog().component().id(),
                                EvaluationStatus.ACTIVE))
                .isEqualTo(3);
    }

    @Test
    void cancelledAssignmentsAreExcludedAndCanBeReassignedOnce() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.REVIEW);
        tx().executeWithoutResult(
                        status ->
                                evaluations
                                        .findByIdForUpdate(fixture.evaluationId())
                                        .orElseThrow()
                                        .setStatus(EvaluationStatus.CANCELLED));
        var request = reviewer(fixture, lecturerThree);
        var outcomes =
                race(
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(List.of(request)),
                                        admin.getId()),
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(List.of(request)),
                                        admin.getId()));
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
        assertThat(
                        evaluations.countByAssignmentIdAndComponentIdAndStatus(
                                fixture.projectId(),
                                fixture.catalog().component().id(),
                                EvaluationStatus.ACTIVE))
                .isEqualTo(1);
        assertThat(
                        evaluations.countByAssignmentIdAndComponentIdAndStatus(
                                fixture.projectId(),
                                fixture.catalog().component().id(),
                                EvaluationStatus.CANCELLED))
                .isEqualTo(1);
    }

    @Test
    void mainAndSecondarySupervisorsCannotBeAssignedEvenInMixedBatches() throws Exception {
        var fixture = fixture(false, AssessmentComponentType.REVIEW);
        var outcomes =
                race(
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(
                                                List.of(
                                                        reviewer(fixture, lecturerFour),
                                                        reviewer(fixture, lecturerOne))),
                                        admin.getId()),
                        () ->
                                batches.assign(
                                        new BatchEvaluationRequest(
                                                List.of(
                                                        reviewer(fixture, lecturerFive),
                                                        reviewer(fixture, lecturerTwo))),
                                        admin.getId()));
        assertThat(outcomes)
                .allSatisfy(
                        outcome ->
                                assertThat(outcome.error())
                                        .isInstanceOf(BusinessRuleException.class));
        assertThat(
                        evaluations.findByAssignmentIdAndStatus(
                                fixture.projectId(), EvaluationStatus.ACTIVE))
                .hasSize(1);
    }

    @Autowired private EvaluationCommitteeService evaluationCommitteeService;

    @Autowired
    private com.example.qldetai.feature.evaluation.repository.CommitteeMemberRepository
            committeeMembers;

    @Test
    void concurrentCommitteeMembersCannotExceedTheConfiguredMaximum() throws Exception {
        Long committee =
                tx().execute(
                                status -> {
                                    var configured =
                                            registrationPeriodRepository
                                                    .findById(period.getId())
                                                    .orElseThrow();
                                    configured.setMaxCommitteeMembers(3);
                                    Long id =
                                            evaluationCommitteeService.createCommittee(
                                                    new CreateCommitteeRequest(
                                                            period.getId(), "Concurrent committee"),
                                                    admin.getId());
                                    evaluationCommitteeService.addMember(
                                            id,
                                            new AddCommitteeMemberRequest(
                                                    lecturerOne.getId(), 1, CommitteeRole.CHAIR),
                                            admin.getId());
                                    evaluationCommitteeService.addMember(
                                            id,
                                            new AddCommitteeMemberRequest(
                                                    lecturerTwo.getId(),
                                                    2,
                                                    CommitteeRole.SECRETARY),
                                            admin.getId());
                                    return id;
                                });
        oneWinner(
                race(
                        () -> {
                            evaluationCommitteeService.addMember(
                                    committee,
                                    new AddCommitteeMemberRequest(
                                            lecturerThree.getId(), 3, CommitteeRole.MEMBER),
                                    admin.getId());
                            return true;
                        },
                        () -> {
                            evaluationCommitteeService.addMember(
                                    committee,
                                    new AddCommitteeMemberRequest(
                                            lecturerFour.getId(), 3, CommitteeRole.MEMBER),
                                    admin.getId());
                            return true;
                        }));
        assertThat(committeeMembers.findByCommitteeId(committee)).hasSize(3);
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
        if (System.getProperty("race.test.mysql.url") != null) {
            // Advance the fixture from proposal to registration without overlapping SQL time
            // windows.
            period.setProposalEndAt(nowUtc().minusDays(1));
            period.setRegistrationStartAt(nowUtc().minusHours(1));
            period.setRegistrationEndAt(nowUtc().plusDays(2));
            registrationPeriodRepository.saveAndFlush(period);
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
            String code, String name, AssessmentComponentType componentType) {
        AssessmentComponentResponse component =
                evaluationService.createComponent(
                        new CreateAssessmentComponentRequest(
                                period.getId(), unique(code), name, componentType),
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
        var existing =
                semesterRepository.findAll().stream()
                        .filter(
                                item ->
                                        item.getAcademicYear().equals("2026-2027")
                                                && item.getSemesterNo() == 1)
                        .findFirst();
        if (existing.isPresent()) return existing.get();
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
        if (System.getProperty("race.test.mysql.url") != null) {
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
