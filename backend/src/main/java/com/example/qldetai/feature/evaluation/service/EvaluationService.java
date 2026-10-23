package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponent;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.domain.DefenseSession;
import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.domain.Rubric;
import com.example.qldetai.feature.evaluation.domain.RubricCriterion;
import com.example.qldetai.feature.evaluation.domain.ScoreDetail;
import com.example.qldetai.feature.evaluation.domain.ScoreSheet;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.AssessmentComponentResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateAssessmentComponentRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateEvaluationRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateRubricRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CriterionRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CriterionResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.EvaluationAssignmentResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.RubricResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.SaveSheetRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ScoreRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ScoreSheetResponse;
import com.example.qldetai.feature.evaluation.repository.AssessmentComponentRepository;
import com.example.qldetai.feature.evaluation.repository.CommitteeMemberRepository;
import com.example.qldetai.feature.evaluation.repository.DefenseSessionRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.GradeChangeRequestRepository;
import com.example.qldetai.feature.evaluation.repository.ResultInputRepository;
import com.example.qldetai.feature.evaluation.repository.RubricCriterionRepository;
import com.example.qldetai.feature.evaluation.repository.RubricRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreDetailRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_LOCKED = "LOCKED";

    private final EvaluationReadScope readScope;
    private final EvaluationCatalogAccess catalogAccess;
    private final AssessmentComponentRepository components;
    private final RubricRepository rubrics;
    private final RubricCriterionRepository criteria;
    private final EvaluationAssignmentRepository evaluations;
    private final ScoreSheetRepository sheets;
    private final ScoreDetailRepository details;
    private final GradeChangeRequestRepository gradeChangeRequests;
    private final ResultInputRepository resultInputs;
    private final ProjectAssignmentRepository assignments;
    private final RegistrationPeriodRepository registrationPeriods;
    private final DefenseSessionRepository defenses;
    private final CommitteeMemberRepository committeeMembers;
    private final LecturerRepository lecturers;
    private final UserRepository users;
    private final TopicSupervisorRepository supervisors;
    private final Clock clock;
    private final com.example.qldetai.feature.academic.service.AcademicAuthorization academicAuthorization;
    private final EntityManager entityManager;
    private final NotificationService notifications;
    private final com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository
            appointments;

    @Value("${app.email.display-time-zone:Asia/Ho_Chi_Minh}")
    private String displayTimeZone;

    @Transactional(readOnly = true)
    public List<AssessmentComponentResponse> listComponents(Long periodId, Long actorId) {
        return components
                .findAll(catalogAccess.components(periodId, actorId), Sort.by("code").ascending())
                .stream()
                .map(this::component)
                .toList();
    }

    @Transactional
    @BusinessAudit(action = "ASSESSMENT_COMPONENT_CREATE", entityType = "ASSESSMENT_COMPONENT")
    public AssessmentComponentResponse createComponent(
            CreateAssessmentComponentRequest request, Long actorId) {
        manager(actorId);
        academicAuthorization.requirePeriodManagement(request.periodId(), actorId);
        RegistrationPeriod period =
                registrationPeriods
                        .findById(request.periodId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Registration period was not found."));
        String code = normalizeCode(request.code());
        if (components.existsByPeriodIdAndCodeIgnoreCase(period.getId(), code)) {
            throw new ConflictException(
                    "This registration period already has an assessment component with that code.");
        }
        AssessmentComponent component = new AssessmentComponent();
        component.setPeriod(period);
        component.setCode(code);
        component.setName(request.name().trim());
        component.setComponentType(request.componentType());
        component.setStatus(STATUS_DRAFT);
        return component(components.save(component));
    }

    @Transactional
    @BusinessAudit(
            action = "ASSESSMENT_COMPONENT_LOCK",
            entityType = "ASSESSMENT_COMPONENT",
            entityIdArgument = 0)
    public AssessmentComponentResponse lockComponent(Long componentId, Long actorId) {
        manager(actorId);
        AssessmentComponent component = componentForUpdate(componentId);
        academicAuthorization.requirePeriodManagement(component.getPeriod().getId(), actorId);
        if (!STATUS_DRAFT.equals(component.getStatus())) {
            throw new BusinessRuleException("Only a DRAFT assessment component can be locked.");
        }
        boolean hasLockedRubric = rubrics.existsByComponentIdAndStatus(componentId, STATUS_LOCKED);
        if (!hasLockedRubric) {
            throw new BusinessRuleException(
                    "Lock at least one rubric before locking this assessment component.");
        }
        component.setStatus(STATUS_LOCKED);
        return component(component);
    }

    @Transactional(readOnly = true)
    public List<RubricResponse> listRubrics(Long componentId, Long actorId) {
        List<Rubric> found =
                rubrics.findAll(
                        catalogAccess.rubrics(componentId, actorId),
                        Sort.by("versionNo").descending());
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RubricCriterion>> criteriaByRubric =
                criteria
                        .findByRubricIdInOrderBySortOrder(
                                found.stream().map(Rubric::getId).toList())
                        .stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        criterion -> criterion.getRubric().getId()));
        return found.stream()
                .map(
                        rubric ->
                                rubric(
                                        rubric,
                                        criteriaByRubric.getOrDefault(rubric.getId(), List.of())))
                .toList();
    }

    @Transactional
    @BusinessAudit(action = "RUBRIC_CREATE", entityType = "RUBRIC")
    public RubricResponse createRubric(CreateRubricRequest request, Long actorId) {
        manager(actorId);
        validateCriteria(request.criteria());
        AssessmentComponent component = componentForUpdate(request.componentId());
        academicAuthorization.requirePeriodManagement(component.getPeriod().getId(), actorId);
        if (STATUS_LOCKED.equals(component.getStatus())) {
            throw new BusinessRuleException(
                    "A locked assessment component cannot receive a new rubric.");
        }

        Rubric rubric = new Rubric();
        rubric.setComponent(component);
        rubric.setName(request.name().trim());
        rubric.setVersionNo(
                rubrics.findFirstByComponentIdOrderByVersionNoDesc(component.getId())
                        .map(latest -> Math.addExact(latest.getVersionNo(), 1))
                        .orElse(1));
        rubric.setStatus(STATUS_DRAFT);
        rubric = rubrics.save(rubric);

        for (CriterionRequest criterionRequest : request.criteria()) {
            RubricCriterion criterion = new RubricCriterion();
            criterion.setRubric(rubric);
            criterion.setName(criterionRequest.name().trim());
            criterion.setDescription(criterionRequest.description().trim());
            criterion.setMaxScore(criterionRequest.maxScore());
            criterion.setSortOrder(criterionRequest.sortOrder());
            criteria.save(criterion);
        }
        return rubric(rubric);
    }

    @Transactional
    @BusinessAudit(action = "RUBRIC_LOCK", entityType = "RUBRIC", entityIdArgument = 0)
    public RubricResponse lockRubric(Long rubricId, Long actorId) {
        manager(actorId);
        Rubric rubric = rubricEntity(rubricId);
        academicAuthorization.requirePeriodManagement(rubric.getComponent().getPeriod().getId(), actorId);
        if (!STATUS_DRAFT.equals(rubric.getStatus())) {
            throw new BusinessRuleException("Only a DRAFT rubric can be locked.");
        }

        List<RubricCriterion> rubricCriteria = criteria.findByRubricIdOrderBySortOrder(rubricId);
        if (rubricCriteria.isEmpty()) {
            throw new BusinessRuleException(
                    "Add at least one criterion before locking this rubric.");
        }

        BigDecimal totalScore =
                rubricCriteria.stream()
                        .map(RubricCriterion::getMaxScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalScore.compareTo(BigDecimal.TEN) != 0) {
            throw new BusinessRuleException(
                    "Rubric criteria must total exactly 10 points before locking.");
        }

        rubric.setStatus(STATUS_LOCKED);
        return rubric(rubric);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationAssignmentResponse> listAssignments(
            Long periodId,
            Long assignmentId,
            Long lecturerId,
            EvaluationStatus status,
            int page,
            int size) {
        return listAssignments(periodId, assignmentId, lecturerId, status, page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationAssignmentResponse> listAssignments(
            Long periodId,
            Long assignmentId,
            Long lecturerId,
            EvaluationStatus status,
            int page,
            int size,
            Long actorId) {
        Specification<EvaluationAssignment> specification = readScope.assignments(actorId);
        if (periodId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("period").get("id"), periodId));
        }
        if (assignmentId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("assignment").get("id"), assignmentId));
        }
        if (lecturerId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("lecturer").get("id"), lecturerId));
        }
        if (status != null) {
            specification =
                    specification.and(
                            (root, query, builder) -> builder.equal(root.get("status"), status));
        }

        Page<EvaluationAssignment> found =
                evaluations.findAll(
                        specification,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by("deadlineAt").ascending().and(Sort.by("id").descending())));
        Map<Long, ScoreSheet> sheetsByEvaluation =
                found.isEmpty()
                        ? Map.of()
                        : sheets
                                .findByEvaluationIdIn(
                                        found.getContent().stream()
                                                .map(EvaluationAssignment::getId)
                                                .toList())
                                .stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                sheet -> sheet.getEvaluation().getId(),
                                                sheet -> sheet));
        List<Long> submittedSheetIds = sheetsByEvaluation.values().stream()
                .filter(sheet -> sheet.getStatus() == ScoreSheetStatus.SUBMITTED
                        || sheet.getStatus() == ScoreSheetStatus.LOCKED)
                .map(ScoreSheet::getId).toList();
        Map<Long, BigDecimal> submittedTotals = submittedSheetIds.isEmpty() ? Map.of()
                : details.totalsBySheetIds(submittedSheetIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ScoreDetailRepository.SheetTotal::getSheetId,
                                ScoreDetailRepository.SheetTotal::getTotalScore));
        return PageResponse.from(
                found.map(
                        evaluation ->
                                assignment(
                                        evaluation, sheetsByEvaluation.get(evaluation.getId()), submittedTotals)));
    }

    @Transactional
    @BusinessAudit(action = "EVALUATION_ASSIGN", entityType = "EVALUATION_ASSIGNMENT")
    public Long assign(CreateEvaluationRequest request, Long actorId) {
        manager(actorId);
        ProjectAssignment assignment =
                assignments
                        .findByIdForUpdate(request.assignmentId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Project assignment was not found."));
        academicAuthorization.requireDepartmentManagement(assignment.getTopic().getDepartment().getId(), actorId);
        Rubric rubric = rubricEntity(request.rubricId());
        Lecturer lecturer =
                lecturers
                        .findById(request.lecturerId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Lecturer was not found."));
        AssessmentComponent component = rubric.getComponent();

        validateScoringDeadline(component, assignment, request.deadlineAt());

        if (!component.getPeriod().getId().equals(assignment.getPeriod().getId())) {
            throw new BusinessRuleException(
                    "Rubric and assignment must belong to the same registration period.");
        }
        if (!STATUS_LOCKED.equals(component.getStatus())) {
            throw new BusinessRuleException(
                    "Only a locked assessment component can receive evaluator assignments.");
        }
        if (!STATUS_LOCKED.equals(rubric.getStatus())) {
            throw new BusinessRuleException("Only a locked rubric can be used for evaluation.");
        }
        if (supervisors.findByTopicIdOrderBySlotNoAsc(assignment.getTopic().getId()).stream()
                .anyMatch(
                        supervisor -> supervisor.getLecturer().getId().equals(lecturer.getId()))) {
            throw new BusinessRuleException("A supervisor cannot evaluate their own topic.");
        }
        List<EvaluationAssignment> activeEvaluations =
                evaluations.findActiveForUpdate(assignment.getId(), component.getId());
        if (activeEvaluations.stream()
                .anyMatch(existing -> existing.getLecturer().getId().equals(lecturer.getId()))) {
            throw new ConflictException(
                    "This lecturer already evaluates this assessment component for the project.");
        }

        DefenseSession defense = null;
        String evaluatorRole;
        if (component.getComponentType() == AssessmentComponentType.REVIEW) {
            if (request.defenseId() != null) {
                throw new BusinessRuleException(
                        "A review evaluation must not be attached to a defense session.");
            }
            evaluatorRole = "REVIEWER";
        } else if (component.getComponentType() == AssessmentComponentType.COMMITTEE) {
            ensureReviewCompleteBeforeCommittee(assignment);
            if (request.defenseId() == null) {
                throw new BusinessRuleException(
                        "A committee evaluation requires a defense session.");
            }
            defense =
                    defenses.findById(request.defenseId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Defense session was not found."));
            if (!defense.getAssignment().getId().equals(assignment.getId())) {
                throw new BusinessRuleException(
                        "The defense session does not belong to the selected project assignment.");
            }
            if (defense.getStatus() == com.example.qldetai.feature.evaluation.domain.DefenseStatus.CANCELLED) {
                throw new BusinessRuleException("Không thể phân công chấm cho phiên bảo vệ đã hủy.");
            }
            boolean memberOfCommittee =
                    committeeMembers.findByCommitteeId(defense.getCommittee().getId()).stream()
                            .anyMatch(
                                    member ->
                                            member.getLecturer().getId().equals(lecturer.getId()));
            if (!memberOfCommittee) {
                throw new BusinessRuleException(
                        "The lecturer must belong to the defense committee.");
            }
            evaluatorRole = "COMMITTEE_MEMBER";
        } else {
            if (request.defenseId() != null) {
                throw new BusinessRuleException(
                        "This assessment component does not accept a defense session.");
            }
            evaluatorRole = "OTHER";
        }

        EvaluationAssignment evaluationAssignment = new EvaluationAssignment();
        evaluationAssignment.setAssignment(assignment);
        evaluationAssignment.setComponent(component);
        evaluationAssignment.setRubric(rubric);
        evaluationAssignment.setLecturer(lecturer);
        evaluationAssignment.setDefense(defense);
        evaluationAssignment.setEvaluatorRole(evaluatorRole);
        evaluationAssignment.setDeadlineAt(request.deadlineAt());
        evaluationAssignment = evaluations.save(evaluationAssignment);
        notifyEvaluator(evaluationAssignment);
        return evaluationAssignment.getId();
    }

    private void notifyEvaluator(EvaluationAssignment evaluation) {
        var assignment = evaluation.getAssignment();
        var zone = java.time.ZoneId.of(displayTimeZone);
        var deadline =
                evaluation
                        .getDeadlineAt()
                        .atOffset(java.time.ZoneOffset.UTC)
                        .atZoneSameInstant(zone);
        String content =
                "Bạn được phân công chấm đề tài "
                        + assignment.getTopic().getCode()
                        + " - "
                        + assignment.getTopic().getTitle()
                        + ".\n"
                        + "Nhóm: "
                        + assignment.getGroup().getName()
                        + ".\n"
                        + "Thành phần đánh giá: "
                        + evaluation.getComponent().getName()
                        + ".\n"
                        + "Bộ tiêu chí: "
                        + evaluation.getRubric().getName()
                        + ".\n"
                        + "Hạn chấm: "
                        + deadline.format(
                                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + " ("
                        + zone.getId()
                        + ").\n"
                        + "Vui lòng mở mục Phiếu điểm để xem tiêu chí và chấm điểm.";
        notifications.publish(
                evaluation.getLecturer().getUser().getId(),
                "evaluation-assigned-" + evaluation.getId(),
                "Phân công chấm điểm",
                content,
                "/score-sheets");
    }

    @Transactional
    @BusinessAudit(action = "SCORE_SHEET_SAVE", entityType = "SCORE_SHEET", entityIdArgument = 0)
    public ScoreSheetResponse saveSheet(Long evaluationId, SaveSheetRequest request, Long actorId) {
        // Serialize review completion with other submissions and evaluator assignments.
        // Read only the ID before locking, so a waiting transaction has no stale entity state.
        Long projectId =
                evaluations
                        .findProjectIdByEvaluationId(evaluationId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Evaluation assignment was not found."));
        assignments
                .findByIdForUpdate(projectId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Project assignment was not found."));
        EvaluationAssignment evaluation =
                evaluations
                        .findByIdForUpdate(evaluationId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Evaluation assignment was not found."));
        if (!evaluation.getLecturer().getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("Only the assigned evaluator can score this sheet.");
        }
        if (evaluation.getStatus() != EvaluationStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Only an active evaluation assignment can receive scores.");
        }
        if (evaluation.getDefense() != null
                && evaluation.getDefense().getStatus()
                        == com.example.qldetai.feature.evaluation.domain.DefenseStatus.CANCELLED) {
            throw new BusinessRuleException("Không thể chấm điểm cho phiên bảo vệ đã hủy.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isAfter(evaluation.getDeadlineAt())) {
            throw new BusinessRuleException("The scoring deadline has passed.");
        }
        LocalDateTime reviewDeadline = evaluation.getAssignment().getPeriod().getReviewDeadlineAt();
        if (evaluation.getComponent().getComponentType() == AssessmentComponentType.REVIEW
                && reviewDeadline != null
                && now.isAfter(reviewDeadline)) {
            throw new BusinessRuleException("The registration period review deadline has passed.");
        }

        ScoreSheet sheet =
                sheets.findByEvaluationIdForUpdate(evaluationId)
                        .orElseGet(
                                () -> {
                                    ScoreSheet freshSheet = new ScoreSheet();
                                    freshSheet.setEvaluation(evaluation);
                                    freshSheet.setRubric(evaluation.getRubric());
                                    // Revision zero describes the not-yet-created sheet returned by
                                    // mySheet().
                                    freshSheet.setVersion(1);
                                    return freshSheet;
                                });
        if (sheet.getId() != null && resultInputs.existsBySheetId(sheet.getId())) {
            throw new BusinessRuleException(
                    "Cannot reopen or edit a score sheet captured by a result version; finalized and published evidence is immutable.");
        }
        if (sheet.getStatus() != ScoreSheetStatus.DRAFT) {
            throw new BusinessRuleException(
                    "Only a draft score sheet can be edited. Request an approved grade change to reopen it.");
        }
        if (sheet.getId() != null && sheet.getVersion() != request.version()) {
            throw new ConflictException("The score sheet was changed. Reload and try again.");
        }
        int originalVersion = sheet.getVersion();

        Map<Long, RubricCriterion> allowedCriteria = new HashMap<>();
        criteria.findByRubricIdOrderBySortOrder(evaluation.getRubric().getId())
                .forEach(criterion -> allowedCriteria.put(criterion.getId(), criterion));

        if (request.scores() == null || request.scores().size() != allowedCriteria.size()) {
            throw new BusinessRuleException("Every rubric criterion must receive a score.");
        }
        Set<Long> submittedCriterionIds = new HashSet<>();
        for (ScoreRequest score : request.scores()) {
            if (score == null || score.score() == null || score.score().signum() < 0) {
                throw new BusinessRuleException(
                        "A score must be between zero and its criterion maximum.");
            }
            RubricCriterion criterion = allowedCriteria.get(score.criterionId());
            if (criterion == null
                    || !submittedCriterionIds.add(score.criterionId())
                    || score.score().compareTo(criterion.getMaxScore()) > 0) {
                throw new BusinessRuleException(
                        "A score must be between zero and its criterion maximum.");
            }
        }
        if (!submittedCriterionIds.equals(allowedCriteria.keySet())) {
            throw new BusinessRuleException(
                    "Every rubric criterion must receive exactly one score.");
        }

        // MySQL enforces DRAFT -> details -> SUBMITTED. Persist a new sheet in DRAFT first
        // rather than trying to insert a submitted sheet before its score details exist.
        sheet.setOverallComment(request.overallComment());
        sheet.setStatus(ScoreSheetStatus.DRAFT);
        sheet.setSubmittedAt(null);
        sheet = sheets.saveAndFlush(sheet);

        details.deleteBySheetId(sheet.getId());
        // Remove old (sheet, criterion) keys before IDENTITY inserts of replacement details.
        details.flush();
        for (ScoreRequest score : request.scores()) {
            RubricCriterion criterion = allowedCriteria.get(score.criterionId());
            ScoreDetail detail = new ScoreDetail();
            detail.setSheet(sheet);
            detail.setCriterion(criterion);
            detail.setMaxScore(criterion.getMaxScore());
            detail.setScore(score.score());
            detail.setComment(score.comment());
            details.save(detail);
        }
        details.flush();

        if (request.submit()) {
            sheet.setStatus(ScoreSheetStatus.SUBMITTED);
            sheet.setSubmittedAt(LocalDateTime.now(clock));
            sheet = sheets.saveAndFlush(sheet);
        }
        // Details belong to the sheet's version even when its comment/status did not change.
        if (sheet.getVersion() == originalVersion) {
            entityManager.lock(sheet, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }

        if (request.submit()) {
            gradeChangeRequests
                    .findFirstBySheetIdAndStatusOrderByCreatedAtDesc(
                            sheet.getId(), GradeChangeStatus.APPROVED)
                    .filter(requestedChange -> requestedChange.getAppliedAt() == null)
                    .ifPresent(
                            requestedChange -> {
                                requestedChange.setStatus(GradeChangeStatus.APPLIED);
                                requestedChange.setAppliedAt(LocalDateTime.now(clock));
                            });
        }

        if (request.submit()
                && evaluation.getComponent().getComponentType() == AssessmentComponentType.REVIEW) {
            notifyReviewSubmitted(evaluation, sheet);
        }
        return sheet(sheet);
    }

    private void notifyReviewSubmitted(EvaluationAssignment evaluation, ScoreSheet sheet) {
        var project = evaluation.getAssignment();
        var topic = project.getTopic();
        Set<Long> recipients =
                new java.util.LinkedHashSet<>(
                        appointments.findActiveManagerUserIds(
                                topic.getFaculty().getId(),
                                topic.getDepartment().getId(),
                                LocalDateTime.now(clock)));
        users.findActiveAdministrators().forEach(user -> recipients.add(user.getId()));
        String content =
                evaluation.getLecturer().getUser().getFullName()
                        + " đã nộp phiếu điểm phản biện cho đề tài "
                        + topic.getCode()
                        + " - "
                        + topic.getTitle()
                        + ".\nNhóm: "
                        + project.getGroup().getName()
                        + ".\nThành phần đánh giá: "
                        + evaluation.getComponent().getName()
                        + ".";
        String revision = sheet.getId() + "-" + sheet.getVersion();
        for (Long recipient : recipients) {
            notifications.publish(
                    recipient,
                    "review-submitted-" + revision,
                    "Đã nộp phiếu điểm phản biện",
                    content,
                    "/evaluator-assignment");
        }
        try {
            ensureReviewCompleteBeforeCommittee(project);
        } catch (BusinessRuleException incomplete) {
            return;
        }
        for (Long recipient : recipients) {
            notifications.publish(
                    recipient,
                    "review-complete-" + project.getId() + "-" + revision,
                    "Đề tài đã hoàn tất phản biện",
                    "Đề tài "
                            + topic.getCode()
                            + " - "
                            + topic.getTitle()
                            + " đã có đầy đủ phiếu điểm phản biện hợp lệ. Vui lòng kiểm tra điều kiện xếp lịch bảo vệ.",
                    "/evaluator-assignment");
        }
    }

    @Transactional(readOnly = true)
    public com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ReviewProgressResponse
            reviewProgress(Long periodId, Long actorId) {
        Specification<EvaluationAssignment> scope =
                readScope
                        .assignments(actorId)
                        .and(
                                (root, query, cb) ->
                                        cb.and(
                                                cb.equal(root.get("period").get("id"), periodId),
                                                cb.equal(
                                                        root.get("status"),
                                                        EvaluationStatus.ACTIVE),
                                                cb.equal(
                                                        root.get("component").get("componentType"),
                                                        AssessmentComponentType.REVIEW)));
        long assigned = evaluations.count(scope);
        long submitted =
                evaluations.count(
                        scope.and(
                                withSheetStatus(
                                        Set.of(
                                                ScoreSheetStatus.SUBMITTED,
                                                ScoreSheetStatus.LOCKED))));
        long draft = evaluations.count(scope.and(withSheetStatus(Set.of(ScoreSheetStatus.DRAFT))));
        return new com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ReviewProgressResponse(
                assigned, submitted, draft, Math.max(0, assigned - submitted - draft));
    }

    private Specification<EvaluationAssignment> withSheetStatus(Set<ScoreSheetStatus> statuses) {
        return (root, query, cb) -> {
            var found = query.subquery(Long.class);
            var sheet = found.from(ScoreSheet.class);
            found.select(sheet.get("id"))
                    .where(
                            cb.equal(sheet.get("evaluation"), root),
                            sheet.get("status").in(statuses));
            return cb.exists(found);
        };
    }

    private void validateScoringDeadline(
            AssessmentComponent component, ProjectAssignment assignment, LocalDateTime deadline) {
        if (deadline == null || !deadline.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("The scoring deadline must be in the future.");
        }
        LocalDateTime reviewDeadline = assignment.getPeriod().getReviewDeadlineAt();
        if (component.getComponentType() == AssessmentComponentType.REVIEW
                && reviewDeadline != null
                && deadline.isAfter(reviewDeadline)) {
            throw new BusinessRuleException(
                    "The scoring deadline cannot exceed the registration period review deadline.");
        }
    }

    private void ensureReviewCompleteBeforeCommittee(ProjectAssignment assignment) {
        List<AssessmentComponent> reviewComponents =
                components.findByPeriodIdOrderByCodeAsc(assignment.getPeriod().getId()).stream()
                        .filter(
                                component ->
                                        component.getComponentType()
                                                == AssessmentComponentType.REVIEW)
                        .toList();
        if (reviewComponents.isEmpty()) {
            return;
        }
        for (AssessmentComponent reviewComponent : reviewComponents) {
            if (!STATUS_LOCKED.equals(reviewComponent.getStatus())) {
                throw new BusinessRuleException(
                        "Lock every review component before assigning committee evaluators.");
            }
            List<EvaluationAssignment> reviewerAssignments =
                    evaluations.findActiveForUpdate(assignment.getId(), reviewComponent.getId());
            if (reviewerAssignments.isEmpty()
                    || reviewerAssignments.stream()
                            .anyMatch(evaluation -> !hasCompletedScoreSheet(evaluation))) {
                throw new BusinessRuleException(
                        "Complete every reviewer assignment and submitted review score before committee evaluation.");
            }
        }
    }

    private boolean hasCompletedScoreSheet(EvaluationAssignment evaluation) {
        return sheets.findByEvaluationIdForUpdate(evaluation.getId())
                .filter(
                        sheet ->
                                sheet.getStatus() == ScoreSheetStatus.SUBMITTED
                                        || sheet.getStatus() == ScoreSheetStatus.LOCKED)
                .filter(this::hasCompleteScoreDetails)
                .isPresent();
    }

    private boolean hasCompleteScoreDetails(ScoreSheet sheet) {
        if (sheet.getRubric() == null) {
            return false;
        }
        Set<Long> requiredCriterionIds =
                criteria.findByRubricIdOrderBySortOrder(sheet.getRubric().getId()).stream()
                        .map(RubricCriterion::getId)
                        .collect(java.util.stream.Collectors.toSet());
        List<ScoreDetail> scoreDetails = details.findBySheetIdForUpdate(sheet.getId());
        Set<Long> scoredCriterionIds =
                scoreDetails.stream()
                        .map(detail -> detail.getCriterion().getId())
                        .collect(java.util.stream.Collectors.toSet());
        return !requiredCriterionIds.isEmpty()
                && scoreDetails.size() == requiredCriterionIds.size()
                && scoredCriterionIds.equals(requiredCriterionIds);
    }

    @Transactional(readOnly = true)
    public ScoreSheetResponse mySheet(Long evaluationId, Long actorId) {
        EvaluationAssignment evaluation =
                evaluations
                        .findById(evaluationId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Evaluation assignment was not found."));
        if (!evaluation.getLecturer().getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("You cannot view this score sheet.");
        }
        return sheets.findByEvaluationId(evaluationId)
                .map(this::sheet)
                .orElse(
                        new ScoreSheetResponse(
                                null,
                                evaluationId,
                                ScoreSheetStatus.DRAFT,
                                0,
                                BigDecimal.ZERO,
                                null,
                                List.of()));
    }

    private void manager(Long actorId) {
        boolean allowed =
                users.findWithRolesById(actorId)
                        .map(
                                user ->
                                        user.getUserRoles().stream()
                                                .anyMatch(
                                                        role ->
                                                                role.getRole().getCode()
                                                                                == RoleCode.ADMIN
                                                                        || role.getRole().getCode()
                                                                                == RoleCode
                                                                                        .LECTURER))
                        .orElse(false);
        if (!allowed) {
            throw new AccessDeniedException(
                    "Only lecturers or administrators can manage evaluation.");
        }
    }

    private AssessmentComponent componentForUpdate(Long componentId) {
        return components
                .findByIdForUpdate(componentId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Assessment component was not found."));
    }

    private Rubric rubricEntity(Long rubricId) {
        return rubrics.findById(rubricId)
                .orElseThrow(() -> new ResourceNotFoundException("Rubric was not found."));
    }

    private AssessmentComponentResponse component(AssessmentComponent component) {
        return new AssessmentComponentResponse(
                component.getId(),
                component.getPeriod().getId(),
                component.getPeriod().getName(),
                component.getCode(),
                component.getName(),
                component.getComponentType(),
                component.getStatus());
    }

    private RubricResponse rubric(Rubric rubric) {
        return rubric(rubric, criteria.findByRubricIdOrderBySortOrder(rubric.getId()));
    }

    private RubricResponse rubric(Rubric rubric, List<RubricCriterion> rubricCriteria) {
        AssessmentComponent component = rubric.getComponent();
        return new RubricResponse(
                rubric.getId(),
                component.getId(),
                component.getCode(),
                component.getName(),
                rubric.getName(),
                rubric.getVersionNo(),
                rubric.getStatus(),
                rubricCriteria.stream()
                        .map(
                                criterion ->
                                        new CriterionResponse(
                                                criterion.getId(),
                                                criterion.getName(),
                                                criterion.getDescription(),
                                                criterion.getMaxScore(),
                                                criterion.getSortOrder()))
                        .toList());
    }

    private EvaluationAssignmentResponse assignment(
            EvaluationAssignment evaluationAssignment, ScoreSheet scoreSheet,
            Map<Long, BigDecimal> submittedTotals) {
        ProjectAssignment assignment = evaluationAssignment.getAssignment();
        return new EvaluationAssignmentResponse(
                evaluationAssignment.getId(),
                assignment.getId(),
                assignment.getPeriod().getId(),
                assignment.getPeriod().getName(),
                evaluationAssignment.getComponent().getId(),
                evaluationAssignment.getComponent().getCode(),
                evaluationAssignment.getComponent().getName(),
                evaluationAssignment.getRubric().getId(),
                evaluationAssignment.getRubric().getName(),
                evaluationAssignment.getRubric().getVersionNo(),
                evaluationAssignment.getLecturer().getId(),
                evaluationAssignment.getLecturer().getLecturerCode(),
                evaluationAssignment.getLecturer().getUser().getFullName(),
                evaluationAssignment.getEvaluatorRole(),
                evaluationAssignment.getDefense() == null
                        ? null
                        : evaluationAssignment.getDefense().getId(),
                assignment.getGroup().getId(),
                assignment.getGroup().getName(),
                assignment.getTopic().getId(),
                assignment.getTopic().getCode(),
                assignment.getTopic().getTitle(),
                evaluationAssignment.getStatus(),
                evaluationAssignment.getDeadlineAt(),
                scoreSheet == null ? null : scoreSheet.getId(),
                scoreSheet == null ? null : scoreSheet.getStatus(),
                scoreSheet == null ? null : submittedTotals.get(scoreSheet.getId()));
    }

    private ScoreSheetResponse sheet(ScoreSheet scoreSheet) {
        List<ScoreRequest> scores =
                details.findBySheetId(scoreSheet.getId()).stream()
                        .map(
                                detail ->
                                        new ScoreRequest(
                                                detail.getCriterion().getId(),
                                                detail.getScore(),
                                                detail.getComment()))
                        .toList();
        BigDecimal totalScore =
                scores.stream().map(ScoreRequest::score).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ScoreSheetResponse(
                scoreSheet.getId(),
                scoreSheet.getEvaluation().getId(),
                scoreSheet.getStatus(),
                scoreSheet.getVersion(),
                totalScore,
                scoreSheet.getOverallComment(),
                scores);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void validateCriteria(List<CriterionRequest> requestedCriteria) {
        if (requestedCriteria == null || requestedCriteria.isEmpty()) {
            throw new BusinessRuleException("Add at least one rubric criterion.");
        }
        Set<Integer> sortOrders = new HashSet<>();
        for (CriterionRequest criterion : requestedCriteria) {
            if (criterion == null
                    || criterion.name() == null
                    || criterion.name().isBlank()
                    || criterion.description() == null
                    || criterion.description().isBlank()
                    || criterion.maxScore() == null
                    || criterion.maxScore().signum() <= 0
                    || criterion.maxScore().compareTo(BigDecimal.TEN) > 0
                    || criterion.sortOrder() < 1) {
                throw new BusinessRuleException(
                        "Rubric criteria require a name, description, positive maximum up to 10, and positive sort order.");
            }
            if (!sortOrders.add(criterion.sortOrder())) {
                throw new BusinessRuleException("Rubric criterion sort orders must be unique.");
            }
        }
    }
}
