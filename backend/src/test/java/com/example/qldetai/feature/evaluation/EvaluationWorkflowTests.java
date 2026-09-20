package com.example.qldetai.feature.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.feature.academic.RegistrationPeriod;
import com.example.qldetai.feature.academic.RegistrationPeriodRepository;
import com.example.qldetai.feature.communication.NotificationService;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.CreateGradeChangeRequest;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.ReviewGradeChangeRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateEvaluationRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateRubricRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CriterionRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.SaveSheetRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.ScoreRequest;
import com.example.qldetai.feature.groupregistration.GroupMemberRepository;
import com.example.qldetai.feature.identity.Lecturer;
import com.example.qldetai.feature.identity.LecturerRepository;
import com.example.qldetai.feature.identity.Role;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.StudentRepository;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.project.ProjectAssignment;
import com.example.qldetai.feature.project.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.Topic;
import com.example.qldetai.feature.topic.TopicSupervisorRepository;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

class EvaluationWorkflowTests {

    private final AssessmentComponentRepository components = mock(AssessmentComponentRepository.class);
    private final RubricRepository rubrics = mock(RubricRepository.class);
    private final RubricCriterionRepository criteria = mock(RubricCriterionRepository.class);
    private final EvaluationAssignmentRepository evaluations = mock(EvaluationAssignmentRepository.class);
    private final ScoreSheetRepository sheets = mock(ScoreSheetRepository.class);
    private final ScoreDetailRepository details = mock(ScoreDetailRepository.class);
    private final GradeChangeRequestRepository changes = mock(GradeChangeRequestRepository.class);
    private final ProjectAssignmentRepository assignments = mock(ProjectAssignmentRepository.class);
    private final RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
    private final DefenseSessionRepository defenses = mock(DefenseSessionRepository.class);
    private final CommitteeMemberRepository committeeMembers = mock(CommitteeMemberRepository.class);
    private final LecturerRepository lecturers = mock(LecturerRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TopicSupervisorRepository supervisors = mock(TopicSupervisorRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneOffset.UTC);

    private final EvaluationService service = new EvaluationService(
            components, rubrics, criteria, evaluations, sheets, details, changes, assignments,
            periods, defenses, committeeMembers, lecturers, users, supervisors, clock);
    private final EvaluationAdministrationService administration = new EvaluationAdministrationService(
            mock(EvaluationCommitteeRepository.class), committeeMembers, defenses, periods, lecturers,
            mock(StudentRepository.class), users, assignments, supervisors, sheets, details, evaluations,
            mock(ResultVersionRepository.class), changes, mock(GroupMemberRepository.class),
            mock(NotificationService.class), clock);

    private RegistrationPeriod period;
    private AssessmentComponent component;
    private ProjectAssignment project;
    private Rubric rubric;
    private Lecturer evaluator;
    private EvaluationAssignment evaluation;
    private ScoreSheet sheet;
    private GradeChangeRequest change;

    @BeforeEach
    void setUp() {
        period = new RegistrationPeriod();
        period.setId(1L);

        User manager = new User();
        manager.setId(10L);
        Role adminRole = new Role();
        adminRole.setCode(RoleCode.ADMIN);
        manager.replaceRoles(Set.of(adminRole), LocalDateTime.now(clock));
        when(users.findWithRolesById(10L)).thenReturn(Optional.of(manager));
        when(users.findById(10L)).thenReturn(Optional.of(manager));

        User lecturerUser = new User();
        lecturerUser.setId(20L);
        evaluator = new Lecturer();
        evaluator.setId(2L);
        evaluator.setUser(lecturerUser);
        when(lecturers.findById(2L)).thenReturn(Optional.of(evaluator));
        when(users.findById(20L)).thenReturn(Optional.of(lecturerUser));

        Topic topic = new Topic();
        topic.setId(30L);
        project = new ProjectAssignment();
        project.setId(3L);
        project.setPeriod(period);
        project.setTopic(topic);
        when(assignments.findById(3L)).thenReturn(Optional.of(project));

        component = new AssessmentComponent();
        component.setId(4L);
        component.setPeriod(period);
        component.setComponentType(AssessmentComponentType.REVIEW);
        component.setExpectedEvaluators(3);
        component.setStatus("LOCKED");
        when(components.findById(4L)).thenReturn(Optional.of(component));
        rubric = new Rubric();
        rubric.setId(5L);
        rubric.setComponent(component);
        rubric.setStatus("LOCKED");
        when(rubrics.findById(5L)).thenReturn(Optional.of(rubric));

        evaluation = new EvaluationAssignment();
        evaluation.setId(6L);
        evaluation.setLecturer(evaluator);
        evaluation.setRubric(rubric);
        evaluation.setAssignment(project);
        evaluation.setComponent(component);
        when(evaluations.findById(6L)).thenReturn(Optional.of(evaluation));
        when(evaluations.save(any(EvaluationAssignment.class))).thenAnswer(invocation -> {
            EvaluationAssignment saved = invocation.getArgument(0);
            saved.setId(6L);
            return saved;
        });

        sheet = new ScoreSheet();
        sheet.setId(7L);
        sheet.setEvaluation(evaluation);
        sheet.setRubric(rubric);
        sheet.setVersion(2);
        when(sheets.findByEvaluationId(6L)).thenReturn(Optional.of(sheet));
        when(sheets.findById(7L)).thenReturn(Optional.of(sheet));
        when(sheets.saveAndFlush(any(ScoreSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RubricCriterion first = criterion(8L);
        RubricCriterion second = criterion(9L);
        when(criteria.findByRubricIdOrderBySortOrder(5L)).thenReturn(List.of(first, second));

        change = new GradeChangeRequest();
        change.setId(11L);
        change.setSheet(sheet);
        change.setRequestedBy(lecturerUser);
        when(changes.findById(11L)).thenReturn(Optional.of(change));
    }

    @Test
    void committeeAssignmentRequiresDefense() {
        component.setComponentType(AssessmentComponentType.COMMITTEE);

        assertThatThrownBy(() -> service.assign(assignmentRequest(null), 10L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("requires a defense");
        verify(evaluations, never()).save(any());
    }

    @Test
    void reviewAssignmentRejectsDefense() {
        assertThatThrownBy(() -> service.assign(assignmentRequest(12L), 10L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("must not be attached");
    }

    @Test
    void reviewAssignmentStoresReviewerAndNullDefense() {
        assertThat(service.assign(assignmentRequest(null), 10L)).isEqualTo(6L);

        ArgumentCaptor<EvaluationAssignment> saved = ArgumentCaptor.forClass(EvaluationAssignment.class);
        verify(evaluations).save(saved.capture());
        assertThat(saved.getValue().getEvaluatorRole()).isEqualTo("REVIEWER");
        assertThat(saved.getValue().getDefense()).isNull();
    }

    @Test
    void committeeAssignmentStoresSelectedDefenseAndCommitteeRole() {
        DefenseSession defense = committeeDefense();
        CommitteeMember member = new CommitteeMember();
        member.setLecturer(evaluator);
        when(committeeMembers.findByCommitteeId(13L)).thenReturn(List.of(member));

        service.assign(assignmentRequest(12L), 10L);

        ArgumentCaptor<EvaluationAssignment> saved = ArgumentCaptor.forClass(EvaluationAssignment.class);
        verify(evaluations).save(saved.capture());
        assertThat(saved.getValue().getDefense()).isSameAs(defense);
        assertThat(saved.getValue().getEvaluatorRole()).isEqualTo("COMMITTEE_MEMBER");
    }

    @Test
    void committeeAssignmentRejectsDifferentProject() {
        DefenseSession defense = committeeDefense();
        ProjectAssignment otherProject = new ProjectAssignment();
        otherProject.setId(99L);
        defense.setAssignment(otherProject);

        assertThatThrownBy(() -> service.assign(assignmentRequest(12L), 10L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("selected project");
    }

    @Test
    void committeeAssignmentRejectsDifferentPeriod() {
        DefenseSession defense = committeeDefense();
        RegistrationPeriod otherPeriod = new RegistrationPeriod();
        otherPeriod.setId(99L);
        defense.setPeriod(otherPeriod);

        assertThatThrownBy(() -> service.assign(assignmentRequest(12L), 10L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("same registration period");
    }

    @Test
    void committeeAssignmentRejectsLecturerOutsideCommittee() {
        committeeDefense();

        assertThatThrownBy(() -> service.assign(assignmentRequest(12L), 10L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("belong to the defense committee");
    }

    @Test
    void duplicateActiveEvaluatorIsRejectedBeforeInsert() {
        when(evaluations.existsByAssignmentIdAndComponentIdAndLecturerIdAndStatus(
                3L, 4L, 2L, EvaluationStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(assignmentRequest(null), 10L))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already assigned");
        verify(evaluations, never()).save(any());
    }

    @Test
    void approvalIsFlushedBeforeReopeningSheet() {
        sheet.setStatus(ScoreSheetStatus.LOCKED);
        sheet.setSubmittedAt(LocalDateTime.now(clock).minusDays(1));
        doAnswer(invocation -> {
            assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
            assertThat(sheet.getStatus()).isEqualTo(ScoreSheetStatus.LOCKED);
            return null;
        }).when(changes).flush();

        administration.reviewChange(11L, new ReviewGradeChangeRequest(true, "Approved"), 10L);

        assertThat(sheet.getStatus()).isEqualTo(ScoreSheetStatus.DRAFT);
        assertThat(sheet.getSubmittedAt()).isNull();
        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
        assertThat(change.getAppliedAt()).isNull();
        var order = inOrder(changes, sheets);
        order.verify(changes).flush();
        order.verify(sheets).flush();
    }

    @Test
    void rejectedChangeLeavesSheetLocked() {
        sheet.setStatus(ScoreSheetStatus.LOCKED);

        administration.reviewChange(11L, new ReviewGradeChangeRequest(false, "Rejected"), 10L);

        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.REJECTED);
        assertThat(sheet.getStatus()).isEqualTo(ScoreSheetStatus.LOCKED);
        assertThat(change.getAppliedAt()).isNull();
    }

    @Test
    void successfulDraftSaveAppliesApprovedChange() {
        approvedChange();

        service.saveSheet(6L, validScores(false), 20L);

        assertThat(sheet.getStatus()).isEqualTo(ScoreSheetStatus.DRAFT);
        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPLIED);
        assertThat(change.getAppliedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void submissionWritesDetailsWhileDraftAndAppliesChangeAfterSheetFlush() {
        approvedChange();
        doAnswer(invocation -> {
            assertThat(sheet.getStatus()).isEqualTo(ScoreSheetStatus.DRAFT);
            assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
            return invocation.getArgument(0);
        }).when(details).save(any(ScoreDetail.class));
        doAnswer(invocation -> {
            assertThat(sheet.getStatus()).isEqualTo(ScoreSheetStatus.SUBMITTED);
            assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
            return null;
        }).when(sheets).flush();

        service.saveSheet(6L, validScores(true), 20L);

        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPLIED);
        var order = inOrder(details, sheets, changes);
        order.verify(sheets).saveAndFlush(sheet);
        order.verify(details).deleteBySheetId(7L);
        order.verify(details).flush();
        order.verify(details, org.mockito.Mockito.times(2)).save(any(ScoreDetail.class));
        order.verify(details).flush();
        order.verify(sheets).flush();
        order.verify(changes).findFirstBySheetIdAndStatusOrderByCreatedAtDesc(7L, GradeChangeStatus.APPROVED);
    }

    @Test
    void failedDetailWriteDoesNotApplyChange() {
        approvedChange();
        when(details.save(any(ScoreDetail.class))).thenThrow(new DataIntegrityViolationException("Write failed"));

        assertThatThrownBy(() -> service.saveSheet(6L, validScores(true), 20L))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
        assertThat(change.getAppliedAt()).isNull();
        verify(changes, never()).findFirstBySheetIdAndStatusOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void failedSubmissionFlushDoesNotApplyChange() {
        approvedChange();
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("Submission rejected"))
                .when(sheets).flush();

        assertThatThrownBy(() -> service.saveSheet(6L, validScores(true), 20L))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
        assertThat(change.getAppliedAt()).isNull();
        verify(changes, never()).findFirstBySheetIdAndStatusOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void responseContainsVersionAfterFlush() {
        doAnswer(invocation -> {
            sheet.setVersion(4);
            return null;
        }).when(sheets).flush();

        assertThat(service.saveSheet(6L, validScores(true), 20L).version()).isEqualTo(4);
    }

    @Test
    void negativeScoreDoesNotApplyApprovedChange() {
        approvedChange();
        SaveSheetRequest request = new SaveSheetRequest(2, null,
                List.of(new ScoreRequest(8L, new BigDecimal("-1"), null),
                        new ScoreRequest(9L, BigDecimal.ONE, null)), false);

        assertThatThrownBy(() -> service.saveSheet(6L, request, 20L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("between zero");
        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
        verify(sheets, never()).saveAndFlush(any());
    }

    @Test
    void duplicateCriterionCannotReplaceMissingCriterion() {
        SaveSheetRequest request = new SaveSheetRequest(2, null,
                List.of(new ScoreRequest(8L, BigDecimal.ONE, null),
                        new ScoreRequest(8L, BigDecimal.ONE, null)), false);

        assertThatThrownBy(() -> service.saveSheet(6L, request, 20L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exactly one score");
        verify(details, never()).deleteBySheetId(anyLong());
    }

    @Test
    void differentLecturerCannotSaveReopenedSheet() {
        approvedChange();

        assertThatThrownBy(() -> service.saveSheet(6L, validScores(false), 99L))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
    }

    @Test
    void staleVersionCannotApplyChange() {
        approvedChange();
        sheet.setVersion(3);

        assertThatThrownBy(() -> service.saveSheet(6L, validScores(false), 20L))
                .isInstanceOf(ConflictException.class);
        assertThat(change.getStatus()).isEqualTo(GradeChangeStatus.APPROVED);
    }

    @Test
    void submittedSheetMustBeReopenedBeforeEditing() {
        sheet.setStatus(ScoreSheetStatus.SUBMITTED);

        assertThatThrownBy(() -> service.saveSheet(6L, validScores(false), 20L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Request approval");
    }

    @Test
    void submittedSheetCanRequestReopening() {
        sheet.setStatus(ScoreSheetStatus.SUBMITTED);
        when(changes.save(any(GradeChangeRequest.class))).thenAnswer(invocation -> {
            GradeChangeRequest saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        assertThat(administration.requestChange(7L, new CreateGradeChangeRequest("Correct typo"), 20L))
                .isEqualTo(11L);
    }

    @Test
    void duplicateRubricSortOrderIsRejected() {
        component.setStatus("DRAFT");
        CreateRubricRequest request = new CreateRubricRequest(4L, "Rubric", List.of(
                new CriterionRequest("First", "First", new BigDecimal("5"), 1),
                new CriterionRequest("Second", "Second", new BigDecimal("5"), 1)));

        assertThatThrownBy(() -> service.createRubric(request, 10L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different sort order");
        verify(rubrics, never()).save(any());
    }

    @Test
    void validationReachesItemsInsideScoreAndRubricLists() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new SaveSheetRequest(0, null,
                    List.of(new ScoreRequest(null, new BigDecimal("-1"), null)), false)))
                    .hasSize(2);
            assertThat(validator.validate(new CreateRubricRequest(4L, "Rubric",
                    List.of(new CriterionRequest("", "Description", BigDecimal.TEN, 1)))))
                    .hasSize(1);
        }
    }

    private RubricCriterion criterion(Long id) {
        RubricCriterion criterion = new RubricCriterion();
        criterion.setId(id);
        criterion.setRubric(rubric);
        criterion.setMaxScore(new BigDecimal("5"));
        return criterion;
    }

    private CreateEvaluationRequest assignmentRequest(Long defenseId) {
        return new CreateEvaluationRequest(3L, 5L, 2L, defenseId, LocalDateTime.now(clock).plusDays(7));
    }

    private DefenseSession committeeDefense() {
        component.setComponentType(AssessmentComponentType.COMMITTEE);
        EvaluationCommittee committee = new EvaluationCommittee();
        committee.setId(13L);
        committee.setPeriod(period);
        DefenseSession defense = new DefenseSession();
        defense.setId(12L);
        defense.setAssignment(project);
        defense.setPeriod(period);
        defense.setCommittee(committee);
        when(defenses.findById(12L)).thenReturn(Optional.of(defense));
        return defense;
    }

    private void approvedChange() {
        change.setStatus(GradeChangeStatus.APPROVED);
        when(changes.findFirstBySheetIdAndStatusOrderByCreatedAtDesc(7L, GradeChangeStatus.APPROVED))
                .thenReturn(Optional.of(change));
    }

    private SaveSheetRequest validScores(boolean submit) {
        return new SaveSheetRequest(2, "Updated comments",
                List.of(new ScoreRequest(8L, new BigDecimal("4"), null),
                        new ScoreRequest(9L, new BigDecimal("3.5"), null)), submit);
    }
}
