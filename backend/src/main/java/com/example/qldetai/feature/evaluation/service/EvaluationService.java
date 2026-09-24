package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
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
import com.example.qldetai.feature.evaluation.repository.RubricCriterionRepository;
import com.example.qldetai.feature.evaluation.repository.RubricRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreDetailRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_LOCKED = "LOCKED";

    private final AssessmentComponentRepository components;
    private final RubricRepository rubrics;
    private final RubricCriterionRepository criteria;
    private final EvaluationAssignmentRepository evaluations;
    private final ScoreSheetRepository sheets;
    private final ScoreDetailRepository details;
    private final GradeChangeRequestRepository gradeChangeRequests;
    private final ProjectAssignmentRepository assignments;
    private final RegistrationPeriodRepository registrationPeriods;
    private final DefenseSessionRepository defenses;
    private final CommitteeMemberRepository committeeMembers;
    private final LecturerRepository lecturers;
    private final UserRepository users;
    private final TopicSupervisorRepository supervisors;
    private final Clock clock;

    public EvaluationService(
            AssessmentComponentRepository components,
            RubricRepository rubrics,
            RubricCriterionRepository criteria,
            EvaluationAssignmentRepository evaluations,
            ScoreSheetRepository sheets,
            ScoreDetailRepository details,
            GradeChangeRequestRepository gradeChangeRequests,
            ProjectAssignmentRepository assignments,
            RegistrationPeriodRepository registrationPeriods,
            DefenseSessionRepository defenses,
            CommitteeMemberRepository committeeMembers,
            LecturerRepository lecturers,
            UserRepository users,
            TopicSupervisorRepository supervisors,
            Clock clock) {
        this.components = components;
        this.rubrics = rubrics;
        this.criteria = criteria;
        this.evaluations = evaluations;
        this.sheets = sheets;
        this.details = details;
        this.gradeChangeRequests = gradeChangeRequests;
        this.assignments = assignments;
        this.registrationPeriods = registrationPeriods;
        this.defenses = defenses;
        this.committeeMembers = committeeMembers;
        this.lecturers = lecturers;
        this.users = users;
        this.supervisors = supervisors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AssessmentComponentResponse> listComponents(Long periodId) {
        List<AssessmentComponent> found = periodId == null
                ? components.findAll().stream()
                        .sorted(Comparator
                                .comparing((AssessmentComponent component) -> component.getPeriod().getId())
                                .thenComparing(AssessmentComponent::getCode, String.CASE_INSENSITIVE_ORDER))
                        .toList()
                : components.findByPeriodIdOrderByCodeAsc(periodId);
        return found.stream().map(this::component).toList();
    }

    @Transactional
    @BusinessAudit(action = "ASSESSMENT_COMPONENT_CREATE", entityType = "ASSESSMENT_COMPONENT")
    public AssessmentComponentResponse createComponent(CreateAssessmentComponentRequest request, Long actorId) {
        manager(actorId);
        RegistrationPeriod period = registrationPeriods.findById(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration period was not found."));
        String code = normalizeCode(request.code());
        if (components.existsByPeriodIdAndCodeIgnoreCase(period.getId(), code)) {
            throw new ConflictException("This registration period already has an assessment component with that code.");
        }
        if (request.componentType() == AssessmentComponentType.COMMITTEE && request.expectedEvaluators() < 3) {
            throw new BusinessRuleException("A committee component requires three to five evaluators.");
        }

        AssessmentComponent component = new AssessmentComponent();
        component.setPeriod(period);
        component.setCode(code);
        component.setName(request.name().trim());
        component.setComponentType(request.componentType());
        component.setExpectedEvaluators(request.expectedEvaluators());
        component.setStatus(STATUS_DRAFT);
        return component(components.save(component));
    }

    @Transactional
    @BusinessAudit(action = "ASSESSMENT_COMPONENT_LOCK", entityType = "ASSESSMENT_COMPONENT", entityIdArgument = 0)
    public AssessmentComponentResponse lockComponent(Long componentId, Long actorId) {
        manager(actorId);
        AssessmentComponent component = componentEntity(componentId);
        if (!STATUS_DRAFT.equals(component.getStatus())) {
            throw new BusinessRuleException("Only a DRAFT assessment component can be locked.");
        }
        boolean hasLockedRubric = rubrics.findByComponentIdOrderByVersionNoDesc(componentId).stream()
                .anyMatch(rubric -> STATUS_LOCKED.equals(rubric.getStatus()));
        if (!hasLockedRubric) {
            throw new BusinessRuleException("Lock at least one rubric before locking this assessment component.");
        }
        component.setStatus(STATUS_LOCKED);
        return component(component);
    }

    @Transactional(readOnly = true)
    public List<RubricResponse> listRubrics(Long componentId) {
        List<Rubric> found = componentId == null
                ? rubrics.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                : rubrics.findByComponentIdOrderByVersionNoDesc(componentId);
        return found.stream().map(this::rubric).toList();
    }

    @Transactional
    @BusinessAudit(action = "RUBRIC_CREATE", entityType = "RUBRIC")
    public RubricResponse createRubric(CreateRubricRequest request, Long actorId) {
        manager(actorId);
        AssessmentComponent component = componentEntity(request.componentId());
        if (STATUS_LOCKED.equals(component.getStatus())) {
            throw new BusinessRuleException("A locked assessment component cannot receive a new rubric.");
        }

        Rubric rubric = new Rubric();
        rubric.setComponent(component);
        rubric.setName(request.name().trim());
        rubric.setVersionNo((int) rubrics.countByComponentId(component.getId()) + 1);
        rubric.setStatus(STATUS_DRAFT);
        rubric.setCreatedAt(LocalDateTime.now(clock));
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
        if (!STATUS_DRAFT.equals(rubric.getStatus())) {
            throw new BusinessRuleException("Only a DRAFT rubric can be locked.");
        }

        List<RubricCriterion> rubricCriteria = criteria.findByRubricIdOrderBySortOrder(rubricId);
        if (rubricCriteria.isEmpty()) {
            throw new BusinessRuleException("Add at least one criterion before locking this rubric.");
        }

        BigDecimal totalScore = rubricCriteria.stream()
                .map(RubricCriterion::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalScore.compareTo(BigDecimal.TEN) != 0) {
            throw new BusinessRuleException("Rubric criteria must total exactly 10 points before locking.");
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
        Specification<EvaluationAssignment> specification = (root, query, builder) -> builder.conjunction();
        if (periodId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("period").get("id"), periodId));
        }
        if (assignmentId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("assignment").get("id"), assignmentId));
        }
        if (lecturerId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("lecturer").get("id"), lecturerId));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }

        return PageResponse.from(evaluations.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by("deadlineAt").ascending().and(Sort.by("id").descending())))
                .map(this::assignment));
    }

    @Transactional
    @BusinessAudit(action = "EVALUATION_ASSIGN", entityType = "EVALUATION_ASSIGNMENT")
    public Long assign(CreateEvaluationRequest request, Long actorId) {
        manager(actorId);
        ProjectAssignment assignment = assignments.findById(request.assignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Project assignment was not found."));
        Rubric rubric = rubricEntity(request.rubricId());
        Lecturer lecturer = lecturers.findById(request.lecturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer was not found."));
        AssessmentComponent component = rubric.getComponent();

        if (!component.getPeriod().getId().equals(assignment.getPeriod().getId())) {
            throw new BusinessRuleException("Rubric and assignment must belong to the same registration period.");
        }
        if (!STATUS_LOCKED.equals(component.getStatus())) {
            throw new BusinessRuleException("Only a locked assessment component can receive evaluator assignments.");
        }
        if (!STATUS_LOCKED.equals(rubric.getStatus())) {
            throw new BusinessRuleException("Only a locked rubric can be used for evaluation.");
        }
        if (supervisors.findByTopicIdOrderBySlotNoAsc(assignment.getTopic().getId()).stream()
                .anyMatch(supervisor -> supervisor.getLecturer().getId().equals(lecturer.getId()))) {
            throw new BusinessRuleException("A supervisor cannot evaluate their own topic.");
        }
        if (evaluations.countByAssignmentIdAndComponentIdAndStatus(
                        assignment.getId(), component.getId(), EvaluationStatus.ACTIVE)
                >= component.getExpectedEvaluators()) {
            throw new ConflictException("This component already has enough evaluators.");
        }

        DefenseSession defense = null;
        String evaluatorRole;
        if (component.getComponentType() == AssessmentComponentType.REVIEW) {
            if (request.defenseId() != null) {
                throw new BusinessRuleException("A review evaluation must not be attached to a defense session.");
            }
            evaluatorRole = "REVIEWER";
        } else if (component.getComponentType() == AssessmentComponentType.COMMITTEE) {
            if (request.defenseId() == null) {
                throw new BusinessRuleException("A committee evaluation requires a defense session.");
            }
            defense = defenses.findById(request.defenseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Defense session was not found."));
            if (!defense.getAssignment().getId().equals(assignment.getId())) {
                throw new BusinessRuleException("The defense session does not belong to the selected project assignment.");
            }
            boolean memberOfCommittee = committeeMembers.findByCommitteeId(defense.getCommittee().getId()).stream()
                    .anyMatch(member -> member.getLecturer().getId().equals(lecturer.getId()));
            if (!memberOfCommittee) {
                throw new BusinessRuleException("The lecturer must belong to the defense committee.");
            }
            evaluatorRole = "COMMITTEE_MEMBER";
        } else {
            if (request.defenseId() != null) {
                throw new BusinessRuleException("This assessment component does not accept a defense session.");
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
        return evaluations.save(evaluationAssignment).getId();
    }

    @Transactional
    @BusinessAudit(action = "SCORE_SHEET_SAVE", entityType = "SCORE_SHEET", entityIdArgument = 0)
    public ScoreSheetResponse saveSheet(Long evaluationId, SaveSheetRequest request, Long actorId) {
        EvaluationAssignment evaluation = evaluations.findById(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation assignment was not found."));
        if (!evaluation.getLecturer().getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("Only the assigned evaluator can score this sheet.");
        }

        ScoreSheet sheet = sheets.findByEvaluationId(evaluationId).orElseGet(() -> {
            ScoreSheet freshSheet = new ScoreSheet();
            freshSheet.setEvaluation(evaluation);
            freshSheet.setRubric(evaluation.getRubric());
            return freshSheet;
        });
        if (sheet.getStatus() == ScoreSheetStatus.LOCKED) {
            throw new BusinessRuleException("A locked score sheet cannot be edited.");
        }
        if (sheet.getId() != null && sheet.getVersion() != request.version()) {
            throw new ConflictException("The score sheet was changed. Reload and try again.");
        }

        Map<Long, RubricCriterion> allowedCriteria = new HashMap<>();
        criteria.findByRubricIdOrderBySortOrder(evaluation.getRubric().getId())
                .forEach(criterion -> allowedCriteria.put(criterion.getId(), criterion));

        if (request.scores().size() != allowedCriteria.size()) {
            throw new BusinessRuleException("Every rubric criterion must receive a score.");
        }
        for (ScoreRequest score : request.scores()) {
            RubricCriterion criterion = allowedCriteria.get(score.criterionId());
            if (criterion == null || score.score().compareTo(criterion.getMaxScore()) > 0) {
                throw new BusinessRuleException("A score must be between zero and its criterion maximum.");
            }
        }

        sheet.setOverallComment(request.overallComment());
        sheet.setStatus(request.submit() ? ScoreSheetStatus.SUBMITTED : ScoreSheetStatus.DRAFT);
        sheet.setSubmittedAt(request.submit() ? LocalDateTime.now(clock) : null);
        sheet = sheets.save(sheet);

        details.deleteBySheetId(sheet.getId());
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

        gradeChangeRequests.findFirstBySheetIdAndStatusOrderByCreatedAtDesc(sheet.getId(), GradeChangeStatus.APPROVED)
                .filter(requestedChange -> requestedChange.getAppliedAt() == null)
                .ifPresent(requestedChange -> {
                    requestedChange.setStatus(GradeChangeStatus.APPLIED);
                    requestedChange.setAppliedAt(LocalDateTime.now(clock));
                });

        return sheet(sheet);
    }

    @Transactional(readOnly = true)
    public ScoreSheetResponse mySheet(Long evaluationId, Long actorId) {
        EvaluationAssignment evaluation = evaluations.findById(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation assignment was not found."));
        if (!evaluation.getLecturer().getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("You cannot view this score sheet.");
        }
        return sheets.findByEvaluationId(evaluationId)
                .map(this::sheet)
                .orElse(new ScoreSheetResponse(null, evaluationId, ScoreSheetStatus.DRAFT, 0, BigDecimal.ZERO, null, List.of()));
    }

    private void manager(Long actorId) {
        boolean allowed = users.findWithRolesById(actorId)
                .map(user -> user.getUserRoles().stream()
                        .anyMatch(role -> role.getRole().getCode() == RoleCode.ADMIN
                                || role.getRole().getCode() == RoleCode.LECTURER))
                .orElse(false);
        if (!allowed) {
            throw new AccessDeniedException("Only lecturers or administrators can manage evaluation.");
        }
    }

    private AssessmentComponent componentEntity(Long componentId) {
        return components.findById(componentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment component was not found."));
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
                component.getExpectedEvaluators(),
                component.getStatus());
    }

    private RubricResponse rubric(Rubric rubric) {
        AssessmentComponent component = rubric.getComponent();
        return new RubricResponse(
                rubric.getId(),
                component.getId(),
                component.getCode(),
                component.getName(),
                rubric.getName(),
                rubric.getVersionNo(),
                rubric.getStatus(),
                criteria.findByRubricIdOrderBySortOrder(rubric.getId()).stream()
                        .map(criterion -> new CriterionResponse(
                                criterion.getId(),
                                criterion.getName(),
                                criterion.getDescription(),
                                criterion.getMaxScore(),
                                criterion.getSortOrder()))
                        .toList());
    }

    private EvaluationAssignmentResponse assignment(EvaluationAssignment evaluationAssignment) {
        ScoreSheet scoreSheet = sheets.findByEvaluationId(evaluationAssignment.getId()).orElse(null);
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
                evaluationAssignment.getDefense() == null ? null : evaluationAssignment.getDefense().getId(),
                assignment.getGroup().getId(),
                assignment.getGroup().getName(),
                assignment.getTopic().getId(),
                assignment.getTopic().getCode(),
                assignment.getTopic().getTitle(),
                evaluationAssignment.getStatus(),
                evaluationAssignment.getDeadlineAt(),
                scoreSheet == null ? null : scoreSheet.getId(),
                scoreSheet == null ? null : scoreSheet.getStatus());
    }

    private ScoreSheetResponse sheet(ScoreSheet scoreSheet) {
        List<ScoreRequest> scores = details.findBySheetId(scoreSheet.getId()).stream()
                .map(detail -> new ScoreRequest(detail.getCriterion().getId(), detail.getScore(), detail.getComment()))
                .toList();
        BigDecimal totalScore = scores.stream()
                .map(ScoreRequest::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
}
