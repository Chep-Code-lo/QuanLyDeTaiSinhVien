package com.example.qldetai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.feature.academic.domain.*;
import com.example.qldetai.feature.academic.repository.*;
import com.example.qldetai.feature.evaluation.domain.*;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.*;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.*;
import com.example.qldetai.feature.evaluation.repository.*;
import com.example.qldetai.feature.evaluation.service.*;
import com.example.qldetai.feature.groupregistration.domain.*;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.*;
import com.example.qldetai.feature.groupregistration.repository.*;
import com.example.qldetai.feature.groupregistration.service.*;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.*;
import com.example.qldetai.feature.progress.domain.*;
import com.example.qldetai.feature.progress.dto.ProgressDtos.*;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import com.example.qldetai.feature.progress.service.ProgressService;
import com.example.qldetai.feature.project.dto.ProjectDtos.*;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.project.service.ProjectAssignmentService;
import com.example.qldetai.feature.topic.domain.*;
import com.example.qldetai.feature.topic.dto.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import com.example.qldetai.feature.topic.service.TopicService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Real transactions on separate connections; no production datasource is used. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:backend-consistency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "logging.level.org.hibernate.SQL=${audit.sql.level:OFF}",
            "logging.level.org.hibernate.stat=OFF",
            "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackendConsistencyIntegrationTests {
    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager academicFixtureEntityManager;

    @Autowired GroupService groups;
    @Autowired RegistrationService registrations;
    @Autowired ProjectAssignmentService projects;
    @Autowired EvaluationService evaluation;
    @Autowired EvaluationAssignmentBatchService batchAssignments;
    @Autowired CommitteeConfigurationService committeeConfiguration;
    @Autowired EvaluationCommitteeService committees;
    @Autowired DefenseSessionService defenses;
    @Autowired EvaluationResultService results;
    @Autowired GradeChangeService gradeChanges;
    @Autowired GroupInvitationService invitations;
    @Autowired ProgressService progress;
    @Autowired StoredFileRepository files;
    @Autowired FacultyRepository faculties;
    @Autowired DepartmentRepository departments;
    @Autowired SemesterRepository semesters;
    @Autowired RegistrationPeriodRepository periods;
    @Autowired com.example.qldetai.feature.academic.service.RegistrationPeriodService periodService;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AcademicAppointmentRepository auditAppointments;
    @Autowired StudentRepository students;
    @Autowired LecturerRepository lecturers;
    @Autowired TopicRepository topics;
    @Autowired TopicSupervisorRepository supervisors;
    @Autowired TopicService topicService;
    @Autowired StudentGroupRepository groupRepository;
    @Autowired GroupMemberRepository members;
    @Autowired TopicRegistrationRepository registrationRepository;
    @Autowired ProjectAssignmentRepository projectRepository;
    @Autowired RubricRepository rubrics;
    @Autowired ScoreSheetRepository sheets;
    @Autowired ScoreDetailRepository details;
    @Autowired EvaluationAssignmentRepository evaluations;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Clock clock;

    private TransactionTemplate tx;
    private User admin;
    private Department department;
    private RegistrationPeriod period;
    private Student leaderA;
    private Student leaderB;
    private Student member;
    private Student extra;
    private Lecturer lecturerA;
    private Lecturer lecturerB;
    private Lecturer lecturerC;
    private Lecturer lecturerD;
    private String roomPrefix;

    @DynamicPropertySource
    static void isolatedMysql(DynamicPropertyRegistry properties) {
        String url = System.getProperty("audit.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:")
                    || !url.contains("/ql_de_tai_sinh_vien?")) {
                throw new IllegalArgumentException(
                        "Audit MySQL must be an explicitly selected disposable local database.");
            }
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add(
                    "spring.datasource.username",
                    () -> System.getProperty("audit.mysql.user", "root"));
            properties.add(
                    "spring.datasource.password",
                    () -> System.getProperty("audit.mysql.password", "audit-only-local"));
            // Use the authoritative SQL bootstrap, including generated columns and triggers.
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
            properties.add("spring.sql.init.mode", () -> "never");
        }
    }

    @BeforeEach
    void fixture() {
        roomPrefix = unique();
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(
                status -> {
                    Faculty faculty = new Faculty();
                    faculty.setCode(unique());
                    faculty.setName("Audit faculty");
                    faculty.setActive(true);
                    faculties.save(faculty);
                    department = new Department();
                    department.setFaculty(faculty);
                    department.setCode(unique());
                    department.setName("Audit department");
                    department.setActive(true);
                    departments.save(department);
                    admin = user(RoleCode.ADMIN);
        com.example.qldetai.support.AcademicManagerFixture.appointDean(academicFixtureEntityManager, admin, department, now());
                    leaderA = student();
                    leaderB = student();
                    member = student();
                    extra = student();
                    lecturerA = lecturer();
                    lecturerB = lecturer();
                    lecturerC = lecturer();
                    lecturerD = lecturer();
                    Semester semester = new Semester();
                    semester.setAcademicYear(unique());
                    semester.setSemesterNo(1);
                    semester.setStartsOn(now().toLocalDate().minusMonths(1));
                    semester.setEndsOn(now().toLocalDate().plusMonths(4));
                    semesters.save(semester);
                    period = new RegistrationPeriod();
                    period.setFaculty(faculty);
                    period.setSemester(semester);
                    period.setCreatedBy(admin);
                    period.setName("Audit period");
                    period.setProjectType(ProjectType.COURSE);
                    period.setProposalStartAt(now().minusDays(6));
                    period.setProposalEndAt(now().minusDays(4));
                    period.setRegistrationStartAt(now().minusDays(3));
                    period.setRegistrationEndAt(now().plusDays(3));
                    period.setStatus(RegistrationPeriodStatus.ACTIVE);
                    period.setMinGroupMembers(2);
                    period.setMaxGroupMembers(3);
                    periods.save(period);
                });
    }

    @Test
    void simultaneousGroupCreationKeepsOnlyOneMembership() throws Exception {
        List<Object> outcomes = race(() -> createGroup(leaderA), () -> createGroup(leaderA));
        assertOneSuccess(outcomes);
        assertThat(outcomes.stream().filter(Throwable.class::isInstance).toList())
                .allMatch(BusinessRuleException.class::isInstance);
        assertThat(members.findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(leaderA.getId()))
                .hasSize(1);
    }

    @Test
    void simultaneousGroupCreationAcrossPeriodsAllowsOneGroupPerPeriod() throws Exception {
        Long otherPeriod =
                tx.execute(
                        status -> {
                            RegistrationPeriod other = new RegistrationPeriod();
                            other.setFaculty(period.getFaculty());
                            other.setSemester(period.getSemester());
                            other.setName("Other period");
                            other.setCreatedBy(admin);
                            other.setProjectType(ProjectType.COURSE);
                            other.setStatus(RegistrationPeriodStatus.ACTIVE);
                            other.setProposalStartAt(period.getProposalStartAt());
                            other.setProposalEndAt(period.getProposalEndAt());
                            other.setRegistrationStartAt(period.getRegistrationStartAt());
                            other.setRegistrationEndAt(period.getRegistrationEndAt());
                            return periods.save(other).getId();
                        });
        List<Object> outcomes =
                race(
                        () -> createGroup(leaderA),
                        () ->
                                groups.create(
                                        new CreateGroupRequest(otherPeriod, "Other group"),
                                        leaderA.getUser().getId()));
        assertThat(outcomes).allMatch(StudentGroupResponse.class::isInstance);
        assertThat(members.findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(leaderA.getId()))
                .hasSize(2);
    }

    @Test
    void simultaneousMemberAddsCannotPutStudentInTwoGroups() throws Exception {
        Long first = createGroup(leaderA).id();
        Long second = createGroup(leaderB).id();
        assertOneSuccess(
                race(() -> add(first, leaderA, member), () -> add(second, leaderB, member)));
        assertThat(members.findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(member.getId()))
                .hasSize(1);
    }

    @Test
    void concurrentAddsRespectGroupCapacity() throws Exception {
        Long group = activeGroup(leaderA, member).id();
        assertOneSuccess(
                race(() -> add(group, leaderA, leaderB), () -> add(group, leaderA, extra)));
        assertThat(members.countByGroupIdAndLeftAtIsNull(group)).isEqualTo(3);
    }

    @Test
    void parallelInvitationAcceptsCannotJoinTwoGroups() throws Exception {
        Long first = createGroup(leaderA).id();
        Long second = createGroup(leaderB).id();
        var left =
                invitations.create(
                        new CreateGroupInvitationRequest(
                                first, member.getStudentCode(), now().plusHours(1)),
                        leaderA.getUser().getId());
        var right =
                invitations.create(
                        new CreateGroupInvitationRequest(
                                second, member.getStudentCode(), now().plusHours(1)),
                        leaderB.getUser().getId());
        assertOneSuccess(
                race(
                        () -> invitations.accept(left.id(), member.getUser().getId()),
                        () -> invitations.accept(right.id(), member.getUser().getId())));
        assertThat(members.findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(member.getId()))
                .hasSize(1);
    }

    @Test
    void sameGroupCannotCreateTwoPendingRegistrations() throws Exception {
        Long group = activeGroup(leaderA, member).id();
        Long firstTopic = topic();
        Long secondTopic = topic();
        assertOneSuccess(
                race(
                        () -> register(group, firstTopic, leaderA),
                        () -> register(group, secondTopic, leaderA)));
        assertThat(
                        registrationRepository.findByPeriodId(
                                period.getId(), org.springframework.data.domain.Pageable.unpaged()))
                .hasSize(1);
    }

    @Test
    void removingMemberAndRegisteringTopicCannotBothSucceed() throws Exception {
        Long group = activeGroup(leaderA, member).id();
        Long topic = topic();
        assertOneSuccess(
                race(
                        () -> register(group, topic, leaderA),
                        () ->
                                groups.removeMember(
                                        group, member.getId(), leaderA.getUser().getId())));
        tx.executeWithoutResult(
                status -> {
                    boolean pending =
                            registrationRepository.existsByGroupIdAndStatusIn(
                                    group, List.of(RegistrationStatus.PENDING));
                    if (pending) {
                        assertThat(members.countByGroupIdAndLeftAtIsNull(group)).isEqualTo(2);
                    } else {
                        assertThat(groupRepository.findById(group).orElseThrow().getStatus())
                                .isEqualTo(GroupStatus.FORMING);
                    }
                });
    }

    @Test
    void parallelReviewsCreateExactlyOneAssignment() throws Exception {
        var registration = register(activeGroup(leaderA, member).id(), topic(), leaderA);
        assertOneSuccess(race(() -> approve(registration), () -> approve(registration)));
        assertThat(projectRepository.findByRegistrationId(registration.id())).isPresent();
        assertThat(projects.list(period.getId(), null, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void twoGroupsCompetingForTopicLeaveOnlyOneApproved() throws Exception {
        Long topic = topic();
        var first = register(activeGroup(leaderA, member).id(), topic, leaderA);
        var second = register(activeGroup(leaderB, extra).id(), topic, leaderB);
        assertOneSuccess(race(() -> approve(first), () -> approve(second)));
        assertThat(projects.list(period.getId(), null, 0, 20).totalElements()).isEqualTo(1);
        assertThat(
                        List.of(
                                registrationRepository
                                        .findById(first.id())
                                        .orElseThrow()
                                        .getStatus(),
                                registrationRepository
                                        .findById(second.id())
                                        .orElseThrow()
                                        .getStatus()))
                .containsExactlyInAnyOrder(
                        RegistrationStatus.APPROVED, RegistrationStatus.REJECTED);
    }

    @Test
    void concurrentManualAssignmentRetriesReturnSameAssignment() throws Exception {
        var registration = register(activeGroup(leaderA, member).id(), topic(), leaderA);
        // Legacy approved registration awaiting its manual assignment.
        tx.executeWithoutResult(
                status -> {
                    var entity = registrationRepository.findById(registration.id()).orElseThrow();
                    entity.setStatus(RegistrationStatus.APPROVED);
                    entity.setReviewedBy(users.getReferenceById(admin.getId()));
                    entity.setReviewedAt(now());
                });
        List<Object> outcomes =
                race(
                        () ->
                                projects.create(
                                        new CreateAssignmentRequest(registration.id()),
                                        admin.getId()),
                        () ->
                                projects.create(
                                        new CreateAssignmentRequest(registration.id()),
                                        admin.getId()));
        assertThat(outcomes).allMatch(ProjectAssignmentResponse.class::isInstance);
        assertThat(((ProjectAssignmentResponse) outcomes.get(0)).id())
                .isEqualTo(((ProjectAssignmentResponse) outcomes.get(1)).id());
    }

    @Test
    void rejectionAndWithdrawalKeepReregistrationHistoryValid() {
        Long group = activeGroup(leaderA, member).id();
        Long topic = topic();
        var first = register(group, topic, leaderA);
        assertThatThrownBy(
                        () ->
                                registrations.review(
                                        first.id(),
                                        new ReviewTopicRegistrationRequest(
                                                first.version(), RegistrationStatus.REJECTED, " "),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
        registrations.review(
                first.id(),
                new ReviewTopicRegistrationRequest(
                        first.version(), RegistrationStatus.REJECTED, "Revise"),
                admin.getId());
        var second = register(group, topic, leaderA);
        registrations.withdraw(
                second.id(),
                new WithdrawTopicRegistrationRequest(second.version(), "Withdraw"),
                leaderA.getUser().getId());
        assertThat(register(group, topic, leaderA).status()).isEqualTo(RegistrationStatus.PENDING);
    }

    @Test
    void failedAssignmentRollsBackApprovalAndGroupStatus() {
        Long firstTopic = topic();
        Long secondTopic = topic();
        Long group = activeGroup(leaderA, member).id();
        var first = register(group, firstTopic, leaderA);
        approve(first);
        // Simulate legacy inconsistent data: another pending registration of the already-assigned
        // group.
        Long pending =
                tx.execute(
                        status -> {
                            TopicRegistration entity = new TopicRegistration();
                            entity.setPeriod(periods.getReferenceById(period.getId()));
                            entity.setGroup(groupRepository.getReferenceById(group));
                            entity.setTopic(topics.getReferenceById(secondTopic));
                            entity.setRegisteredBy(students.getReferenceById(leaderA.getId()));
                            entity.setRegisteredAt(now());
                            return registrationRepository.save(entity).getId();
                        });
        assertThatThrownBy(
                        () ->
                                registrations.review(
                                        pending,
                                        new ReviewTopicRegistrationRequest(
                                                0, RegistrationStatus.APPROVED, null),
                                        admin.getId()))
                .isInstanceOf(ConflictException.class);
        var unchanged = registrationRepository.findById(pending).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(unchanged.getReviewedAt()).isNull();
        assertThat(groupRepository.findById(group).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.LOCKED);
        assertThat(projectRepository.findByRegistrationId(pending)).isEmpty();
    }

    @Test
    void simultaneousRubricsReceiveDistinctVersions() throws Exception {
        Long component = component();
        List<Object> outcomes = race(() -> rubric(component), () -> rubric(component));
        assertThat(outcomes).allMatch(RubricResponse.class::isInstance);
        assertThat(evaluation.listRubrics(component, admin.getId()))
                .extracting(RubricResponse::versionNo)
                .containsExactly(2, 1);
    }

    @Test
    void rubricVersionUsesMaximumRatherThanRowCount() {
        Long component = component();
        var first = rubric(component);
        tx.executeWithoutResult(
                status -> rubrics.findById(first.id()).orElseThrow().setVersionNo(7));
        assertThat(rubric(component).versionNo()).isEqualTo(8);
    }

    @Test
    void invalidNestedCriteriaAreRejectedWithoutPartialRubric() {
        Long component = component();
        assertThatThrownBy(
                        () ->
                                evaluation.createRubric(
                                        new CreateRubricRequest(
                                                component,
                                                "Invalid",
                                                List.of(
                                                        new CriterionRequest(
                                                                "Content",
                                                                "Description",
                                                                BigDecimal.valueOf(-1),
                                                                1))),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                evaluation.createRubric(
                                        new CreateRubricRequest(
                                                component,
                                                "Invalid",
                                                List.of(criterion(1), criterion(1))),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(evaluation.listRubrics(component, admin.getId())).isEmpty();
    }

    @Test
    void parallelDistinctEvaluatorAssignmentsAreBothKept() throws Exception {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        List<Object> outcomes =
                race(
                        () -> assign(assignment, rubric.id(), lecturerA),
                        () -> assign(assignment, rubric.id(), lecturerB));
        assertThat(outcomes).allMatch(Long.class::isInstance);
        assertThat(
                        evaluations.countByAssignmentIdAndComponentIdAndStatus(
                                assignment, rubric.componentId(), EvaluationStatus.ACTIVE))
                .isEqualTo(2);
    }

    @Test
    void parallelDuplicateEvaluatorIsRejectedEvenWithSpareCapacity() throws Exception {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        assertOneSuccess(
                race(
                        () -> assign(assignment, rubric.id(), lecturerA),
                        () -> assign(assignment, rubric.id(), lecturerA)));
    }

    @Test
    void draftDetailChangesAdvanceVersionAndRejectStaleSave() {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        Long evaluationId = assign(assignment, rubric.id(), lecturerA);
        var first =
                evaluation.saveSheet(
                        evaluationId, score(rubric, 0, 5, false), lecturerA.getUser().getId());
        var second =
                evaluation.saveSheet(
                        evaluationId,
                        score(rubric, first.version(), 6, false),
                        lecturerA.getUser().getId());
        assertThat(second.version()).isGreaterThan(first.version());
        assertThatThrownBy(
                        () ->
                                evaluation.saveSheet(
                                        evaluationId,
                                        score(rubric, first.version(), 7, false),
                                        lecturerA.getUser().getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(evaluation.mySheet(evaluationId, lecturerA.getUser().getId()).totalScore())
                .isEqualByComparingTo("6");
    }

    @Test
    void simultaneousFirstScoreSavesCannotMixDetails() throws Exception {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        Long evaluationId = assign(assignment, rubric.id(), lecturerA);
        assertOneSuccess(
                race(
                        () ->
                                evaluation.saveSheet(
                                        evaluationId,
                                        score(rubric, 0, 5, false),
                                        lecturerA.getUser().getId()),
                        () ->
                                evaluation.saveSheet(
                                        evaluationId,
                                        score(rubric, 0, 8, false),
                                        lecturerA.getUser().getId())));
        var stored = sheets.findByEvaluationId(evaluationId).orElseThrow();
        assertThat(details.findBySheetId(stored.getId())).hasSize(1);
        assertThat(evaluation.mySheet(evaluationId, lecturerA.getUser().getId()).totalScore())
                .isIn(BigDecimal.valueOf(5).setScale(2), BigDecimal.valueOf(8).setScale(2));
    }

    @Test
    void invalidScoresDoNotErasePreviouslySavedDetails() {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        Long evaluationId = assign(assignment, rubric.id(), lecturerA);
        var first =
                evaluation.saveSheet(
                        evaluationId, score(rubric, 0, 5, false), lecturerA.getUser().getId());
        assertThatThrownBy(
                        () ->
                                evaluation.saveSheet(
                                        evaluationId,
                                        score(rubric, first.version(), -1, false),
                                        lecturerA.getUser().getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(evaluation.mySheet(evaluationId, lecturerA.getUser().getId()).totalScore())
                .isEqualByComparingTo("5");
    }

    @Test
    void simultaneousFinalizationCreatesOnlyOneVersion() throws Exception {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        Long evaluationId = assign(assignment, rubric.id(), lecturerA);
        evaluation.saveSheet(evaluationId, score(rubric, 0, 8, true), lecturerA.getUser().getId());
        assertOneSuccess(
                race(
                        () -> results.finalizeResult(assignment, admin.getId()),
                        () -> results.finalizeResult(assignment, admin.getId())));
    }

    @Test
    void concurrentCommitteeMembersCannotDuplicateOffice() throws Exception {
        Long committee =
                committees.createCommittee(
                        new CreateCommitteeRequest(period.getId(), "Audit committee"),
                        admin.getId());
        assertOneSuccess(
                race(
                        () -> addCommitteeMember(committee, lecturerA, 1, CommitteeRole.CHAIR),
                        () -> addCommitteeMember(committee, lecturerB, 2, CommitteeRole.CHAIR)));
        assertThat(committees.listCommittees(period.getId(), 0, 20).content().getFirst().members())
                .hasSize(1);
    }

    @Test
    void concurrentDefenseSchedulingCannotOverlapCommittee() throws Exception {
        Long first = assignedProject();
        var secondRegistration = register(activeGroup(leaderB, extra).id(), topic(), leaderB);
        approve(secondRegistration);
        Long second =
                projectRepository
                        .findByRegistrationId(secondRegistration.id())
                        .orElseThrow()
                        .getId();
        Long committee =
                committees.createCommittee(
                        new CreateCommitteeRequest(period.getId(), "Audit committee"),
                        admin.getId());
        addCommitteeMember(committee, lecturerA, 1, CommitteeRole.CHAIR);
        addCommitteeMember(committee, lecturerB, 2, CommitteeRole.SECRETARY);
        addCommitteeMember(committee, lecturerD, 3, CommitteeRole.MEMBER);
        LocalDateTime starts = now().plusDays(2);
        LocalDateTime ends = starts.plusHours(1);
        assertOneDefenseSuccess(
                race(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                first, committee, roomPrefix + "-A1", starts, ends),
                                        admin.getId()),
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                second,
                                                committee,
                                                roomPrefix + "-A2",
                                                starts,
                                                ends),
                                        admin.getId())));
        assertThat(defenses.listDefenses(period.getId(), committee, 0, 20).totalElements())
                .isEqualTo(1);
    }

    @Test
    void differentCommitteesCannotUseTheSameRoomConcurrently() throws Exception {
        Long first = assignedProject();
        Long second = secondProject();
        Long left = fullCommittee(lecturerA, lecturerB, lecturerD);
        Long right = tx.execute(status -> fullCommittee(lecturer(), lecturer(), lecturer()));
        LocalDateTime starts = now().plusDays(2);
        assertOneDefenseSuccess(
                race(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                first,
                                                left,
                                                " " + roomPrefix + "-Room-A ",
                                                starts,
                                                starts.plusHours(1)),
                                        admin.getId()),
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                second,
                                                right,
                                                roomPrefix + "-room-a",
                                                starts,
                                                starts.plusHours(1)),
                                        admin.getId())));
    }

    @Test
    void sharedLecturerCannotAttendOverlappingCommittees() throws Exception {
        Long first = assignedProject();
        Long second = secondProject();
        Long left = fullCommittee(lecturerA, lecturerB, lecturerD);
        Long right = tx.execute(status -> fullCommittee(lecturerA, lecturer(), lecturer()));
        LocalDateTime starts = now().plusDays(2);
        assertOneDefenseSuccess(
                race(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                first,
                                                left,
                                                roomPrefix + "-B1",
                                                starts,
                                                starts.plusHours(1)),
                                        admin.getId()),
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                second,
                                                right,
                                                roomPrefix + "-B2",
                                                starts.plusMinutes(30),
                                                starts.plusHours(2)),
                                        admin.getId())));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Phòng ", "phòng ", "PHONG ", ""})
    void roomAliasesCannotBypassConflictChecks(String prefix) throws Exception {
        Long first = assignedProject();
        Long second = secondProject();
        Long left = fullCommittee(lecturerA, lecturerB, lecturerD);
        Long right = tx.execute(status -> fullCommittee(lecturer(), lecturer(), lecturer()));
        LocalDateTime starts = now().plusDays(2);
        // The stored label simulates a legacy room; normalization must not rewrite it.
        String legacyLabel = "\u00a0Phòng " + roomPrefix + " A 101";
        defenses.schedule(
                new CreateDefenseRequest(first, left, legacyLabel, starts, starts.plusHours(1)),
                admin.getId());
        assertThatThrownBy(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                second,
                                                right,
                                                prefix
                                                        + roomPrefix.toLowerCase(
                                                                java.util.Locale.ROOT)
                                                        + "A101",
                                                starts,
                                                starts.plusHours(1)),
                                        admin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Phòng");
        assertThat(
                        defenses.listDefenses(period.getId(), left, 0, 20)
                                .content()
                                .getFirst()
                                .location())
                .isEqualTo(legacyLabel);
    }

    @Test
    void concurrentRoomAliasesAllowOnlyOneScheduledSession() throws Exception {
        Long first = assignedProject();
        Long second = secondProject();
        Long left = fullCommittee(lecturerA, lecturerB, lecturerD);
        Long right = tx.execute(status -> fullCommittee(lecturer(), lecturer(), lecturer()));
        LocalDateTime starts = now().plusDays(2);
        assertOneDefenseSuccess(
                race(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                first,
                                                left,
                                                "Phòng " + roomPrefix + " A 101",
                                                starts,
                                                starts.plusHours(1)),
                                        admin.getId()),
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                second,
                                                right,
                                                roomPrefix + "A101",
                                                starts,
                                                starts.plusHours(1)),
                                        admin.getId())));
        assertThat(defenses.listDefenses(period.getId(), null, 0, 20).totalElements()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {2, 17})
    void existingGraduationSchedulePreventsChangingThePeriodDate(int utcHour) {
        Long project = assignedProject();
        Long committee = fullCommittee(lecturerA, lecturerB, lecturerD);
        // Keep the whole session on one Vietnamese calendar day, regardless of the
        // time the test runs. UTC 17:30 also exercises the following Vietnamese date.
        LocalDateTime starts = now().plusDays(5).toLocalDate().atTime(utcHour, 30);
        tx.executeWithoutResult(
                status -> {
                    var current = periods.findById(period.getId()).orElseThrow();
                    current.setProjectType(ProjectType.GRADUATION);
                    current.setReviewDeadlineAt(starts.minusHours(1));
                    current.setDefenseDate(
                            com.example.qldetai.common.time.VietnamTime.dateOf(starts));
                });
        defenses.schedule(
                new CreateDefenseRequest(
                        project, committee, roomPrefix, starts, starts.plusHours(1)),
                admin.getId());
        var current = periodService.get(period.getId());
        var request =
                new com.example.qldetai.feature.academic.dto.AcademicDtos
                        .UpdateRegistrationPeriodRequest(
                        current.version(),
                        current.semesterId(),
                        current.name(),
                        current.projectType(),
                        current.proposalStartAt(),
                        current.proposalEndAt(),
                        current.registrationStartAt(),
                        current.registrationEndAt(),
                        current.reviewDeadlineAt(),
                        current.defenseDate().plusDays(1));
        assertThatThrownBy(() -> periodService.update(current.id(), request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only a draft");
        assertThat(periodService.get(current.id()).defenseDate()).isEqualTo(current.defenseDate());
    }

    @Test
    void studentDefenseListFetchesDisplayRelationsInOneQuery() {
        Long project = assignedProject();
        Long committee = fullCommittee(lecturerA, lecturerB, lecturerD);
        LocalDateTime starts = now().plusDays(2);
        defenses.schedule(
                new CreateDefenseRequest(
                        project, committee, roomPrefix, starts, starts.plusHours(1)),
                admin.getId());
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        tx.executeWithoutResult(
                status -> {
                    entityManager.clear();
                    statistics.clear();
                    var list = defenses.listDefensesForStudent(leaderA.getUser().getId());
                    assertThat(list).hasSize(1);
                    assertThat(list.getFirst().groupName()).isNotBlank();
                    assertThat(list.getFirst().topicTitle()).isNotBlank();
                    assertThat(list.getFirst().committeeName()).isNotBlank();
                    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
                });
    }

    @Test
    void adjacentSessionsAreAllowedButDuplicateProjectsAndInvalidTimesAreRejected() {
        Long first = assignedProject();
        Long second = secondProject();
        Long committee = fullCommittee(lecturerA, lecturerB, lecturerD);
        LocalDateTime starts = now().plusDays(2).withNano(123456789);
        assertThatThrownBy(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                first,
                                                committee,
                                                roomPrefix + "-C1",
                                                starts,
                                                starts),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
        defenses.schedule(
                new CreateDefenseRequest(
                        first, committee, roomPrefix + "-C1", starts, starts.plusHours(1)),
                admin.getId());
        defenses.schedule(
                new CreateDefenseRequest(
                        second,
                        committee,
                        roomPrefix + "-C1",
                        starts.plusHours(1),
                        starts.plusHours(2)),
                admin.getId());
        assertThatThrownBy(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                first,
                                                committee,
                                                roomPrefix + "-C2",
                                                starts.plusDays(1),
                                                starts.plusDays(1).plusHours(1)),
                                        admin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("phiên bảo vệ");
    }

    @Test
    void missingSecretaryAndDuplicateMembersAreRejected() {
        Long project = assignedProject();
        Long committee =
                committees.createCommittee(
                        new CreateCommitteeRequest(period.getId(), "Incomplete"), admin.getId());
        addCommitteeMember(committee, lecturerA, 1, CommitteeRole.CHAIR);
        addCommitteeMember(committee, lecturerB, 2, CommitteeRole.MEMBER);
        addCommitteeMember(committee, lecturerD, 3, CommitteeRole.MEMBER);
        assertThatThrownBy(() -> addCommitteeMember(committee, lecturerA, 4, CommitteeRole.MEMBER))
                .isInstanceOf(ConflictException.class);
        LocalDateTime starts = now().plusDays(2);
        assertThatThrownBy(
                        () ->
                                defenses.schedule(
                                        new CreateDefenseRequest(
                                                project,
                                                committee,
                                                roomPrefix + "-D1",
                                                starts,
                                                starts.plusHours(1)),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void failedBatchRollsBackEarlierAssignmentsAndSuccessfulRetriesAreIdempotent() {
        Long project = assignedProject();
        var rubric = lockedRubric();
        var first =
                new CreateEvaluationRequest(
                        project, rubric.id(), lecturerA.getId(), null, now().plusHours(1));
        var invalid =
                new CreateEvaluationRequest(
                        project, rubric.id(), lecturerB.getId(), null, now().minusHours(1));
        assertThatThrownBy(
                        () ->
                                batchAssignments.assign(
                                        new BatchEvaluationRequest(List.of(first, invalid)),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(evaluation.listAssignments(null, project, null, null, 0, 20).totalElements())
                .isZero();
        assertThat(
                        batchAssignments
                                .assign(new BatchEvaluationRequest(List.of(first)), admin.getId())
                                .assignedCount())
                .isEqualTo(1);
        assertThat(
                        batchAssignments
                                .assign(new BatchEvaluationRequest(List.of(first)), admin.getId())
                                .skippedCount())
                .isEqualTo(1);
    }

    private Long secondProject() {
        var registration = register(activeGroup(leaderB, extra).id(), topic(), leaderB);
        approve(registration);
        return projectRepository.findByRegistrationId(registration.id()).orElseThrow().getId();
    }

    private Long fullCommittee(Lecturer chair, Lecturer secretary, Lecturer member) {
        Long id =
                committees.createCommittee(
                        new CreateCommitteeRequest(period.getId(), "Audit committee"),
                        admin.getId());
        addCommitteeMember(id, chair, 1, CommitteeRole.CHAIR);
        addCommitteeMember(id, secretary, 2, CommitteeRole.SECRETARY);
        addCommitteeMember(id, member, 3, CommitteeRole.MEMBER);
        return id;
    }

    @Test
    void committeeConfigurationIsAtomicAndChecksVersionAndRoster() {
        var component =
                evaluation.createComponent(
                        new CreateAssessmentComponentRequest(
                                period.getId(),
                                unique(),
                                "Committee criteria",
                                AssessmentComponentType.COMMITTEE),
                        admin.getId());
        var rubric = rubric(component.id());
        evaluation.lockRubric(rubric.id(), admin.getId());
        evaluation.lockComponent(component.id(), admin.getId());
        var roster =
                List.of(
                        new CommitteeMemberInput(lecturerA.getId(), "Chair"),
                        new CommitteeMemberInput(lecturerB.getId(), "Secretary"),
                        new CommitteeMemberInput(lecturerD.getId(), "Member"));
        var saved =
                committeeConfiguration.save(
                        null,
                        new SaveCommitteeRequest(
                                period.getId(), "Configured committee", rubric.id(), roster, null),
                        admin.getId());
        assertThat(saved.members()).hasSize(3);
        assertThat(saved.rubricId()).isEqualTo(rubric.id());
        var changed =
                committeeConfiguration.save(
                        saved.id(),
                        new SaveCommitteeRequest(
                                period.getId(),
                                "Renamed committee",
                                rubric.id(),
                                roster,
                                saved.version()),
                        admin.getId());
        assertThat(changed.version()).isGreaterThan(saved.version());
        assertThatThrownBy(
                        () ->
                                committeeConfiguration.save(
                                        saved.id(),
                                        new SaveCommitteeRequest(
                                                period.getId(),
                                                "Stale edit",
                                                rubric.id(),
                                                roster,
                                                saved.version()),
                                        admin.getId()))
                .isInstanceOf(ConflictException.class);
        var invalid =
                List.of(
                        new CommitteeMemberInput(lecturerA.getId(), "Chair"),
                        new CommitteeMemberInput(lecturerA.getId(), "Secretary"),
                        new CommitteeMemberInput(lecturerD.getId(), "Member"));
        assertThatThrownBy(
                        () ->
                                committeeConfiguration.save(
                                        saved.id(),
                                        new SaveCommitteeRequest(
                                                period.getId(),
                                                "Invalid edit",
                                                rubric.id(),
                                                invalid,
                                                changed.version()),
                                        admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(committees.listCommittees(period.getId(), 0, 10).content())
                .anySatisfy(
                        item -> {
                            assertThat(item.name()).isEqualTo("Renamed committee");
                            assertThat(item.members()).hasSize(3);
                        });
    }

    @Test
    void evaluationListsApplyActorScopeBeforePaginationAndRespectAppointmentExpiry() {
        Long project = assignedProject();
        var rubric = lockedRubric();
        assign(project, rubric.id(), lecturerA);
        assign(project, rubric.id(), lecturerB);
        assertThat(
                        evaluation
                                .listAssignments(
                                        period.getId(),
                                        null,
                                        null,
                                        null,
                                        0,
                                        1,
                                        lecturerA.getUser().getId())
                                .totalElements())
                .isEqualTo(1);
        assertThat(
                        evaluation
                                .listAssignments(
                                        period.getId(),
                                        null,
                                        lecturerB.getId(),
                                        null,
                                        0,
                                        1,
                                        lecturerA.getUser().getId())
                                .totalElements())
                .isZero();
        assertThat(
                        evaluation
                                .listAssignments(
                                        period.getId(),
                                        null,
                                        null,
                                        null,
                                        0,
                                        1,
                                        lecturerD.getUser().getId())
                                .totalElements())
                .isZero();
        Long appointmentId =
                tx.execute(
                        status -> {
                            AcademicAppointment appointment = new AcademicAppointment();
                            appointment.setLecturer(lecturerD);
                            appointment.setFaculty(period.getFaculty());
                            appointment.setPositionCode(PositionCode.DEAN);
                            appointment.setStartsAt(now().minusDays(1));
                            appointment.setAssignedBy(admin);
                            return auditAppointments.saveAndFlush(appointment).getId();
                        });
        assertThat(
                        evaluation
                                .listAssignments(
                                        period.getId(),
                                        null,
                                        null,
                                        null,
                                        0,
                                        1,
                                        lecturerD.getUser().getId())
                                .totalElements())
                .isEqualTo(2);
        tx.executeWithoutResult(
                status ->
                        auditAppointments
                                .findById(appointmentId)
                                .orElseThrow()
                                .setEndsAt(now().minusHours(1)));
        assertThat(
                        evaluation
                                .listAssignments(
                                        period.getId(),
                                        null,
                                        null,
                                        null,
                                        0,
                                        1,
                                        lecturerD.getUser().getId())
                                .totalElements())
                .isZero();
        Long committee = fullCommittee(lecturerA, lecturerB, lecturerD);
        var start = now().plusDays(2);
        defenses.schedule(
                new CreateDefenseRequest(
                        project, committee, roomPrefix + "-Scope room", start, start.plusHours(1)),
                admin.getId());
        assertThat(
                        defenses.listDefenses(
                                        period.getId(), null, 0, 1, lecturerA.getUser().getId())
                                .totalElements())
                .isEqualTo(1);
        assertThat(
                        defenses.listDefenses(
                                        period.getId(), null, 0, 1, lecturerC.getUser().getId())
                                .totalElements())
                .isZero();
        assertThat(
                        committees
                                .listCommittees(period.getId(), 0, 1, lecturerC.getUser().getId())
                                .totalElements())
                .isZero();
    }

    @Test
    void topicSearchFiltersInDatabaseWithStablePaginationAndLiteralWildcards() {
        tx.executeWithoutResult(
                status -> {
                    for (int index = 0; index < 250; index++) {
                        Topic topic = new Topic();
                        topic.setPeriod(period);
                        topic.setFaculty(period.getFaculty());
                        topic.setDepartment(department);
                        topic.setCode("PAGING-" + index);
                        topic.setTitle("Paged search topic " + index);
                        topic.setDescription("Description");
                        topic.setObjectives("Objectives");
                        topic.setExpectedOutcomes("Outcomes");
                        topic.setProposedBy(admin);
                        topic.setProposalSource(ProposalSource.LECTURER);
                        topic.setVisibilityType(VisibilityType.OPEN);
                        topic.setStatus(TopicStatus.PENDING);
                        topics.save(topic);
                    }
                });
        var first =
                topicService.list(
                        period.getId(), TopicStatus.PENDING, "Paged search", 0, 10, admin.getId());
        var second =
                topicService.list(
                        period.getId(), TopicStatus.PENDING, "Paged search", 1, 10, admin.getId());
        assertThat(first.totalElements()).isEqualTo(250);
        assertThat(first.content()).hasSize(10);
        assertThat(first.content().stream().map(item -> item.id()).toList())
                .doesNotContainAnyElementsOf(
                        second.content().stream().map(item -> item.id()).toList());
        assertThat(
                        topicService
                                .list(
                                        period.getId(),
                                        TopicStatus.PUBLISHED,
                                        "Paged search",
                                        0,
                                        10,
                                        admin.getId())
                                .totalElements())
                .isZero();
        assertThat(
                        topicService
                                .list(period.getId(), null, "%", 0, 10, admin.getId())
                                .totalElements())
                .isZero();
        assertThat(
                        topicService
                                .list(
                                        period.getId(),
                                        null,
                                        "Audit department",
                                        0,
                                        10,
                                        admin.getId())
                                .totalElements())
                .isEqualTo(250);
        Long supervisedTopic = topic();
        assertThat(
                        topicService
                                .list(period.getId(), null, "Audit user", 0, 10, admin.getId())
                                .content())
                .extracting(item -> item.id())
                .containsExactly(supervisedTopic);
        String code =
                tx.execute(status -> topics.findById(supervisedTopic).orElseThrow().getCode());
        assertThat(
                        topicService
                                .list(period.getId(), null, code, 0, 10, admin.getId())
                                .totalElements())
                .isEqualTo(1);
    }

    @Test
    void concurrentGradeChangeReviewsCannotOverwriteDecision() throws Exception {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        Long evaluationId = assign(assignment, rubric.id(), lecturerA);
        var sheet =
                evaluation.saveSheet(
                        evaluationId, score(rubric, 0, 8, true), lecturerA.getUser().getId());
        Long change =
                gradeChanges.requestChange(
                        sheet.id(),
                        new CreateGradeChangeRequest("Correction"),
                        lecturerA.getUser().getId());
        assertOneSuccess(
                race(
                        () -> {
                            gradeChanges.reviewChange(
                                    change,
                                    new ReviewGradeChangeRequest(true, "Approve"),
                                    admin.getId());
                            return true;
                        },
                        () -> {
                            gradeChanges.reviewChange(
                                    change,
                                    new ReviewGradeChangeRequest(false, "Reject"),
                                    admin.getId());
                            return true;
                        }));
        var stored =
                gradeChanges
                        .listChanges(null, sheet.id(), 0, 20, admin.getId())
                        .content()
                        .getFirst();
        assertThat(evaluation.mySheet(evaluationId, lecturerA.getUser().getId()).status())
                .isEqualTo(
                        stored.status() == GradeChangeStatus.APPROVED
                                ? ScoreSheetStatus.DRAFT
                                : ScoreSheetStatus.SUBMITTED);
    }

    @Test
    void studentProjectMappingDoesNotQueryEachProjectAssociation() {
        for (int index = 0; index < 3; index++) {
            if (index > 0) {
                period =
                        tx.execute(
                                status -> {
                                    RegistrationPeriod other = new RegistrationPeriod();
                                    other.setFaculty(period.getFaculty());
                                    other.setSemester(period.getSemester());
                                    other.setName("Mapping period " + unique());
                                    other.setCreatedBy(admin);
                                    other.setProjectType(ProjectType.COURSE);
                                    other.setStatus(RegistrationPeriodStatus.ACTIVE);
                                    other.setProposalStartAt(period.getProposalStartAt());
                                    other.setProposalEndAt(period.getProposalEndAt());
                                    other.setRegistrationStartAt(period.getRegistrationStartAt());
                                    other.setRegistrationEndAt(period.getRegistrationEndAt());
                                    other.setMinGroupMembers(2);
                                    other.setMaxGroupMembers(3);
                                    return periods.save(other);
                                });
            }
            assignedProject();
        }
        tx.executeWithoutResult(
                status -> {
                    entityManager.clear();
                    var statistics =
                            entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
                    statistics.clear();
                    assertThat(projects.listForStudent(leaderA.getUser().getId()))
                            .hasSize(3)
                            .allSatisfy(
                                    item -> {
                                        assertThat(item.groupName()).isNotBlank();
                                        assertThat(item.topicTitle()).isNotBlank();
                                        assertThat(item.assignedByName()).isNotBlank();
                                    });
                    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
                });
    }

    @Test
    void milestoneListsBatchReportsAndFeedbackWithoutQueriesPerRow() {
        Long project = assignedProject();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long[] queryCounts = new long[2];
        for (int index = 0; index < 3; index++) {
            var milestone =
                    progress.create(
                            new CreateMilestoneRequest(
                                    project,
                                    "Milestone " + index,
                                    MilestoneType.PROGRESS,
                                    "Description",
                                    now().plusDays(1)),
                            admin.getId());
            Long file =
                    tx.execute(
                            status -> {
                                StoredFile stored = new StoredFile();
                                stored.setStorageKey(unique());
                                stored.setOriginalName("report.txt");
                                stored.setMimeType("text/plain");
                                stored.setSizeBytes(1L);
                                stored.setSha256("0".repeat(64));
                                stored.setUploadedBy(
                                        users.getReferenceById(leaderA.getUser().getId()));
                                stored.setUploadedAt(now());
                                stored.setStatus(StoredFileStatus.READY);
                                return files.save(stored).getId();
                            });
            var report =
                    progress.submit(
                            milestone.id(),
                            new SubmitReportRequest(file, "Report"),
                            leaderA.getUser().getId());
            progress.feedback(
                    report.id(),
                    new FeedbackRequest("Complete", true),
                    lecturerC.getUser().getId());
            if (index == 0 || index == 2) {
                int position = index == 0 ? 0 : 1;
                int size = index + 1;
                tx.executeWithoutResult(
                        status -> {
                            entityManager.clear();
                            statistics.clear();
                            assertThat(progress.list(project, admin.getId()))
                                    .hasSize(size)
                                    .allSatisfy(
                                            item ->
                                                    assertThat(item.submissions())
                                                            .hasSize(1)
                                                            .allSatisfy(
                                                                    submission ->
                                                                            assertThat(
                                                                                            submission
                                                                                                    .feedback())
                                                                                    .hasSize(1)));
                            queryCounts[position] = statistics.getPrepareStatementCount();
                        });
            }
        }
        assertThat(queryCounts[1]).isEqualTo(queryCounts[0]);
    }

    @Test
    void concurrentReportSubmissionsReceiveDistinctVersions() throws Exception {
        Long assignment = assignedProject();
        var milestone =
                progress.create(
                        new CreateMilestoneRequest(
                                assignment,
                                "Audit milestone",
                                MilestoneType.PROGRESS,
                                "Description",
                                now().plusDays(1)),
                        admin.getId());
        Long file =
                tx.execute(
                        status -> {
                            StoredFile stored = new StoredFile();
                            stored.setStorageKey(unique());
                            stored.setOriginalName("report.txt");
                            stored.setMimeType("text/plain");
                            stored.setSizeBytes(1L);
                            stored.setSha256("0".repeat(64));
                            stored.setUploadedBy(users.getReferenceById(leaderA.getUser().getId()));
                            stored.setUploadedAt(now());
                            stored.setStatus(StoredFileStatus.READY);
                            return files.save(stored).getId();
                        });
        List<Object> outcomes =
                race(
                        () ->
                                progress.submit(
                                        milestone.id(),
                                        new SubmitReportRequest(file, "First"),
                                        leaderA.getUser().getId()),
                        () ->
                                progress.submit(
                                        milestone.id(),
                                        new SubmitReportRequest(file, "Second"),
                                        leaderA.getUser().getId()));
        assertThat(outcomes).allMatch(SubmissionResponse.class::isInstance);
        assertThat(
                        outcomes.stream()
                                .map(SubmissionResponse.class::cast)
                                .map(SubmissionResponse::versionNo)
                                .toList())
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void supervisorReplacementFlushesOldKeysAndAdvancesTopicRevision() {
        Long topic = topic();
        int version = topics.findById(topic).orElseThrow().getVersion();
        var updated =
                topicService.assignSupervisors(
                        topic,
                        new AssignSupervisorsRequest(version, lecturerA.getId(), lecturerB.getId()),
                        admin.getId());
        assertThat(updated.version()).isGreaterThan(version);
        assertThat(updated.supervisors())
                .extracting(supervisor -> supervisor.lecturerId())
                .containsExactly(lecturerA.getId(), lecturerB.getId());
        assertThatThrownBy(
                        () ->
                                topicService.assignSupervisors(
                                        topic,
                                        new AssignSupervisorsRequest(
                                                version, lecturerC.getId(), null),
                                        admin.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void evaluationListBatchesSheetsAndRetainsPagination() {
        Long assignment = assignedProject();
        var rubric = lockedRubric();
        assign(assignment, rubric.id(), lecturerA);
        assign(assignment, rubric.id(), lecturerB);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        tx.executeWithoutResult(
                status -> {
                    entityManager.clear();
                    statistics.clear();
                    var page =
                            evaluation.listAssignments(
                                    period.getId(), assignment, null, null, 0, 1);
                    assertThat(page.content()).hasSize(1);
                    assertThat(page.totalElements()).isEqualTo(2);
                    assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
                });
    }

    @Test
    void rubricAndGroupListsUseBoundedQueryCounts() {
        Long component = component();
        rubric(component);
        rubric(component);
        rubric(component);
        activeGroup(leaderA, member);
        activeGroup(leaderB, extra);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        tx.executeWithoutResult(
                status -> {
                    entityManager.clear();
                    statistics.clear();
                    assertThat(evaluation.listRubrics(component, admin.getId()))
                            .hasSize(3)
                            .allSatisfy(rubric -> assertThat(rubric.criteria()).hasSize(1));
                    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
                });
        tx.executeWithoutResult(
                status -> {
                    entityManager.clear();
                    statistics.clear();
                    assertThat(groups.list(period.getId(), 0, 1).content()).hasSize(1);
                    assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
                });
    }

    private StudentGroupResponse createGroup(Student leader) {
        return groups.create(
                new CreateGroupRequest(period.getId(), "Audit group"), leader.getUser().getId());
    }

    private Boolean addCommitteeMember(
            Long committee, Lecturer lecturer, int slot, CommitteeRole role) {
        committees.addMember(
                committee,
                new AddCommitteeMemberRequest(lecturer.getId(), slot, role),
                admin.getId());
        return true;
    }

    private StudentGroupResponse add(Long group, Student leader, Student student) {
        return groups.addMember(
                group,
                new AddGroupMemberRequest(student.getStudentCode()),
                leader.getUser().getId());
    }

    private StudentGroupResponse activeGroup(Student leader, Student student) {
        return add(createGroup(leader).id(), leader, student);
    }

    private TopicRegistrationResponse register(Long group, Long topic, Student leader) {
        return registrations.create(
                new CreateTopicRegistrationRequest(group, topic, "Audit"),
                leader.getUser().getId());
    }

    private TopicRegistrationResponse approve(TopicRegistrationResponse registration) {
        return registrations.review(
                registration.id(),
                new ReviewTopicRegistrationRequest(
                        registration.version(), RegistrationStatus.APPROVED, null),
                admin.getId());
    }

    private Long assignedProject() {
        var registration = register(activeGroup(leaderA, member).id(), topic(), leaderA);
        approve(registration);
        return projectRepository.findByRegistrationId(registration.id()).orElseThrow().getId();
    }

    private Long component() {
        return evaluation
                .createComponent(
                        new CreateAssessmentComponentRequest(
                                period.getId(),
                                unique(),
                                "Audit component",
                                AssessmentComponentType.OTHER),
                        admin.getId())
                .id();
    }

    private CriterionRequest criterion(int order) {
        return new CriterionRequest("Content", "Description", BigDecimal.TEN, order);
    }

    private RubricResponse rubric(Long component) {
        return evaluation.createRubric(
                new CreateRubricRequest(component, "Audit rubric", List.of(criterion(1))),
                admin.getId());
    }

    private RubricResponse lockedRubric() {
        Long component = component();
        var rubric = rubric(component);
        rubric = evaluation.lockRubric(rubric.id(), admin.getId());
        evaluation.lockComponent(component, admin.getId());
        return rubric;
    }

    private Long assign(Long project, Long rubric, Lecturer lecturer) {
        return evaluation.assign(
                new CreateEvaluationRequest(
                        project, rubric, lecturer.getId(), null, now().plusDays(1)),
                admin.getId());
    }

    private SaveSheetRequest score(RubricResponse rubric, int version, int value, boolean submit) {
        return new SaveSheetRequest(
                version,
                null,
                List.of(
                        new ScoreRequest(
                                rubric.criteria().getFirst().id(),
                                BigDecimal.valueOf(value),
                                null)),
                submit);
    }

    private Long topic() {
        return tx.execute(
                status -> {
                    Topic topic = new Topic();
                    topic.setPeriod(periods.getReferenceById(period.getId()));
                    topic.setFaculty(period.getFaculty());
                    topic.setDepartment(department);
                    topic.setCode(unique());
                    topic.setTitle("Audit topic");
                    topic.setDescription("Description");
                    topic.setObjectives("Objectives");
                    topic.setExpectedOutcomes("Outcomes");
                    topic.setProposedBy(users.getReferenceById(admin.getId()));
                    topic.setProposalSource(ProposalSource.LECTURER);
                    topic.setVisibilityType(VisibilityType.OPEN);
                    topic.setStatus(TopicStatus.APPROVED);
                    topics.saveAndFlush(topic);
                    TopicSupervisor supervisor = new TopicSupervisor();
                    supervisor.setTopic(topic);
                    supervisor.setLecturer(lecturers.getReferenceById(lecturerC.getId()));
                    supervisor.setSlotNo(1);
                    supervisor.setAssignedBy(users.getReferenceById(admin.getId()));
                    supervisor.setAssignedAt(now());
                    supervisors.saveAndFlush(supervisor);
                    topic.setStatus(TopicStatus.PUBLISHED);
                    topic.setPublishedAt(now());
                    return topics.saveAndFlush(topic).getId();
                });
    }

    private User user(RoleCode role) {
        User user = new User();
        user.setEmail(unique() + "@audit.local");
        user.setFullName("Audit user");
        user.setPasswordHash("unused-in-service-tests");
        user.setStatus(UserStatus.ACTIVE);
        user.replaceRoles(Set.copyOf(roles.findAllByCodeIn(Set.of(role))), now());
        return users.save(user);
    }

    private Student student() {
        Student student = new Student();
        student.setUser(user(RoleCode.STUDENT));
        student.setDepartment(department);
        student.setStudentCode(unique());
        student.setClassName("Audit");
        student.setCohort("2026");
        return students.save(student);
    }

    private Lecturer lecturer() {
        Lecturer lecturer = new Lecturer();
        lecturer.setUser(user(RoleCode.LECTURER));
        lecturer.setDepartment(department);
        lecturer.setLecturerCode(unique());
        return lecturers.save(lecturer);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> runFirst = () -> attempt(barrier, first);
            Callable<Object> runSecond = () -> attempt(barrier, second);
            var left = executor.submit(runFirst);
            var right = executor.submit(runSecond);
            return List.of(left.get(30, TimeUnit.SECONDS), right.get(30, TimeUnit.SECONDS));
        }
    }

    private Object attempt(CyclicBarrier barrier, Callable<?> action) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            return action.call();
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private void assertOneSuccess(List<Object> outcomes) {
        assertThat(outcomes.stream().filter(value -> !(value instanceof Throwable)).count())
                .as(outcomes.toString())
                .isEqualTo(1);
        assertThat(
                        outcomes.stream()
                                .filter(Throwable.class::isInstance)
                                .map(Throwable.class::cast)
                                .toList())
                .allSatisfy(
                        error ->
                                assertThat(error)
                                        .isInstanceOfAny(
                                                BusinessRuleException.class,
                                                ConflictException.class,
                                                org.springframework.dao.ConcurrencyFailureException
                                                        .class,
                                                org.springframework.dao
                                                        .DataIntegrityViolationException.class));
    }

    private void assertOneDefenseSuccess(List<Object> outcomes) {
        assertOneSuccess(outcomes);
        assertThat(outcomes.stream().filter(Throwable.class::isInstance).toList())
                .allMatch(ConflictException.class::isInstance);
    }
}
