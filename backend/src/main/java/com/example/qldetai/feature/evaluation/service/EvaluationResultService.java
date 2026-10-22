package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.email.EmailNotificationEventService;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponent;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.DefenseStatus;
import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.domain.ResultInput;
import com.example.qldetai.feature.evaluation.domain.ResultMember;
import com.example.qldetai.feature.evaluation.domain.ResultStatus;
import com.example.qldetai.feature.evaluation.domain.ResultVersion;
import com.example.qldetai.feature.evaluation.domain.ScoreDetail;
import com.example.qldetai.feature.evaluation.domain.ScoreSheet;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultActionsResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultDetailResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultInputResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultMemberResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultResponse;
import com.example.qldetai.feature.evaluation.repository.AssessmentComponentRepository;
import com.example.qldetai.feature.evaluation.repository.CommitteeMemberRepository;
import com.example.qldetai.feature.evaluation.repository.DefenseSessionRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.ResultInputRepository;
import com.example.qldetai.feature.evaluation.repository.ResultMemberRepository;
import com.example.qldetai.feature.evaluation.repository.ResultVersionRepository;
import com.example.qldetai.feature.evaluation.repository.RubricCriterionRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreDetailRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.project.service.ProjectAuthorization;
import com.example.qldetai.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvaluationResultService {

    private final StudentRepository students;
    private final ProjectAssignmentRepository assignments;
    private final ScoreSheetRepository sheets;
    private final ScoreDetailRepository details;
    private final RubricCriterionRepository criteria;
    private final EvaluationAssignmentRepository evaluations;
    private final ResultVersionRepository results;
    private final ResultInputRepository resultInputs;
    private final ResultMemberRepository resultMembers;
    private final GroupMemberRepository groupMembers;
    private final NotificationService notifications;
    private final EmailNotificationEventService emails;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EvaluationActorAccess actorAccess;
    private final AssessmentComponentRepository components;
    private final DefenseSessionRepository defenses;
    private final CommitteeMemberRepository committeeMembers;
    private final ProjectAuthorization projectAuthorization;
    private final ResultAccess resultAccess;

    @Transactional(readOnly = true)
    public PageResponse<ResultDetailResponse> listResults(
            Long periodId,
            Long assignmentId,
            ResultStatus status,
            int page,
            int size,
            Long actorId) {
        actorAccess.manager(actorId);
        Specification<ResultVersion> specification = resultAccess.readableBy(actorId);
        if (periodId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(
                                            root.join("assignment").join("period").get("id"),
                                            periodId));
        }
        if (assignmentId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("assignment").get("id"), assignmentId));
        }
        if (status != null) {
            specification =
                    specification.and(
                            (root, query, builder) -> builder.equal(root.get("status"), status));
        }
        var found =
                results.findAll(
                        specification,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by("finalizedAt")
                                        .descending()
                                        .and(Sort.by("id").descending())));
        List<Long> ids = found.getContent().stream().map(ResultVersion::getId).toList();
        Map<Long, List<ResultInput>> inputsByResult =
                ids.isEmpty()
                        ? Map.of()
                        : resultInputs.findByResultIdInOrderByIdAsc(ids).stream()
                                .collect(
                                        java.util.stream.Collectors.groupingBy(
                                                input -> input.getResult().getId()));
        Map<Long, List<ResultMember>> membersByResult =
                ids.isEmpty()
                        ? Map.of()
                        : resultMembers.findByResultIdInOrderByIdAsc(ids).stream()
                                .collect(
                                        java.util.stream.Collectors.groupingBy(
                                                member -> member.getResult().getId()));
        return PageResponse.from(
                found.map(
                        result ->
                                resultDetail(
                                        result,
                                        inputsByResult.getOrDefault(result.getId(), List.of()),
                                        membersByResult.getOrDefault(result.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public ResultDetailResponse getResult(Long resultId, Long actorId) {
        ResultVersion resultVersion =
                results.findById(resultId)
                        .orElseThrow(() -> new ResourceNotFoundException("Result was not found."));
        if (actorAccess.isManager(actorId)) {
            if (!results.exists(
                    resultAccess
                            .readableBy(actorId)
                            .and((root, query, cb) -> cb.equal(root.get("id"), resultId)))) {
                throw new AccessDeniedException("You cannot view this result.");
            }
        } else {
            Student student =
                    students.findByUserId(actorId)
                            .orElseThrow(
                                    () ->
                                            new AccessDeniedException(
                                                    "You cannot view this result."));
            if (resultVersion.getStatus() != ResultStatus.PUBLISHED
                    || !resultMembers.existsByResultIdAndStudentId(
                            resultVersion.getId(), student.getId())) {
                throw new AccessDeniedException("You cannot view this result.");
            }
        }
        return resultDetail(resultVersion);
    }

    @Transactional(readOnly = true)
    public List<ResultDetailResponse> listPublishedForStudent(Long userId) {
        return results.findPublishedByStudentUserId(userId).stream()
                .map(this::resultDetail)
                .toList();
    }

    // These flags describe authorization only. Finalization still validates every required
    // assignment and score sheet inside its existing transaction.
    @Transactional(readOnly = true)
    public ResultActionsResponse actions(Long assignmentId, Long actorId) {
        actorAccess.manager(actorId);
        ProjectAssignment assignment =
                assignments
                        .findById(assignmentId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Project assignment was not found."));
        var principal = UserPrincipal.from(actorAccess.user(actorId));
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        boolean canPublish =
                principal.roles().contains(RoleCode.ADMIN)
                        || projectAuthorization.canManageAssignment(assignmentId, authentication);
        boolean canFinalize;
        try {
            requireFinalizer(
                    assignment,
                    components.findByPeriodIdOrderByCodeAsc(assignment.getPeriod().getId()),
                    actorId);
            canFinalize = true;
        } catch (AccessDeniedException denied) {
            canFinalize = false;
        }
        return new ResultActionsResponse(canFinalize, canPublish);
    }

    @Transactional
    @BusinessAudit(action = "RESULT_FINALIZE", entityType = "RESULT_VERSION", entityIdArgument = 0)
    public ResultResponse finalizeResult(Long assignmentId, Long actorId) {
        actorAccess.manager(actorId);
        ProjectAssignment assignment =
                assignments
                        .findByIdForUpdate(assignmentId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Project assignment was not found."));
        List<AssessmentComponent> requiredComponents =
                components.findByPeriodIdOrderByCodeAsc(assignment.getPeriod().getId());
        requireFinalizer(assignment, requiredComponents, actorId);
        List<ResultVersion> currentResults = results.findByAssignmentIdForUpdate(assignmentId);
        if (currentResults.stream()
                .anyMatch(result -> result.getStatus() == ResultStatus.FINALIZED)) {
            throw new BusinessRuleException(
                    "A finalized result is already awaiting publication or supersession.");
        }

        List<EvaluationAssignment> activeEvaluations =
                evaluations.findByAssignmentIdAndStatus(assignmentId, EvaluationStatus.ACTIVE);
        if (activeEvaluations.isEmpty()) {
            throw new BusinessRuleException("This assignment has no evaluation assignments.");
        }

        Map<Long, List<EvaluationAssignment>> byComponent =
                activeEvaluations.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        evaluation -> evaluation.getComponent().getId()));
        Map<Long, ScoreSheet> sourceSheets = new LinkedHashMap<>();
        List<BigDecimal> componentAverages = new java.util.ArrayList<>();
        if (requiredComponents.isEmpty()) {
            throw new BusinessRuleException(
                    "Configure the required assessment components before finalization.");
        }
        for (AssessmentComponent component : requiredComponents) {
            if (!"LOCKED".equals(component.getStatus())) {
                throw new BusinessRuleException(
                        "All required assessment components must be locked before finalization.");
            }
            List<EvaluationAssignment> componentEvaluations =
                    byComponent.getOrDefault(component.getId(), List.of());
            if (componentEvaluations.isEmpty()) {
                throw new BusinessRuleException(
                        "Cannot finalize while a required evaluator assignment is missing.");
            }
            if (component.getComponentType() == AssessmentComponentType.COMMITTEE) {
                requireCompleteCommitteeRoster(assignment, componentEvaluations);
            }
            List<BigDecimal> componentScores = new java.util.ArrayList<>();
            for (EvaluationAssignment evaluation : componentEvaluations) {
                ScoreSheet sheet =
                        sheets.findByEvaluationIdForUpdate(evaluation.getId())
                                .orElseThrow(
                                        () ->
                                                new BusinessRuleException(
                                                        "Cannot finalize while a required score"
                                                                + " sheet is missing."));
                if (sheet.getStatus() != ScoreSheetStatus.SUBMITTED
                        && sheet.getStatus() != ScoreSheetStatus.LOCKED) {
                    throw new BusinessRuleException(
                            "Cannot finalize while a required score sheet is missing.");
                }
                BigDecimal score = scoreTotal(sheet);
                sourceSheets.put(evaluation.getId(), sheet);
                componentScores.add(score);
            }
            componentAverages.add(
                    componentScores.stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(
                                    BigDecimal.valueOf(componentScores.size()),
                                    2,
                                    RoundingMode.HALF_UP));
        }

        BigDecimal finalScore =
                componentAverages.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(componentAverages.size()),
                                2,
                                RoundingMode.HALF_UP);

        ResultVersion resultVersion = new ResultVersion();
        resultVersion.setAssignment(assignment);
        resultVersion.setVersionNo(
                Math.addExact(
                        currentResults.stream()
                                .mapToInt(ResultVersion::getVersionNo)
                                .max()
                                .orElse(0),
                        1));
        resultVersion.setFinalScore(finalScore);
        resultVersion.setFinalizedBy(actorAccess.user(actorId));
        resultVersion.setFinalizedAt(LocalDateTime.now(clock));
        sourceSheets
                .values()
                .forEach(
                        sheet -> {
                            if (sheet.getStatus() == ScoreSheetStatus.SUBMITTED) {
                                sheet.setStatus(ScoreSheetStatus.LOCKED);
                            }
                        });
        sheets.saveAll(sourceSheets.values());
        // The snapshot triggers require the source sheet to already be LOCKED.
        sheets.flush();
        resultVersion = results.saveAndFlush(resultVersion);
        snapshotInputs(resultVersion, activeEvaluations, sourceSheets);
        snapshotMembers(resultVersion, assignment);
        return result(resultVersion);
    }

    private void requireFinalizer(
            ProjectAssignment assignment, List<AssessmentComponent> required, Long actorId) {
        var defense = defenses.findByAssignmentId(assignment.getId());
        boolean needsChair =
                defense.isPresent()
                        || required.stream()
                                .anyMatch(
                                        component ->
                                                component.getComponentType()
                                                        == AssessmentComponentType.COMMITTEE);
        if (needsChair) {
            if (defense.isEmpty()
                    || defense.get().getStatus() == DefenseStatus.CANCELLED
                    || !committeeMembers.existsByCommitteeIdAndLecturerUserIdAndCommitteeRole(
                            defense.get().getCommittee().getId(), actorId, CommitteeRole.CHAIR)) {
                throw new AccessDeniedException(
                        "Only the chair of this project's defense committee can finalize its result.");
            }
            return;
        }
        // Projects without a committee retain the existing scoped academic management workflow.
        var principal = UserPrincipal.from(actorAccess.user(actorId));
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        if (!principal.roles().contains(RoleCode.ADMIN)
                && !projectAuthorization.canManageAssignment(assignment.getId(), authentication)) {
            throw new AccessDeniedException(
                    "Only this project's academic manager can finalize a result without a committee.");
        }
    }

    private void requireCompleteCommitteeRoster(
            ProjectAssignment assignment, List<EvaluationAssignment> componentEvaluations) {
        var defense =
                defenses.findByAssignmentId(assignment.getId())
                        .filter(item -> item.getStatus() != DefenseStatus.CANCELLED)
                        .orElseThrow(
                                () ->
                                        new BusinessRuleException(
                                                "Cannot finalize without an active defense session."));
        Set<Long> memberIds =
                committeeMembers.findByCommitteeId(defense.getCommittee().getId()).stream()
                        .map(member -> member.getLecturer().getId())
                        .collect(java.util.stream.Collectors.toSet());
        Set<Long> evaluatorIds =
                componentEvaluations.stream()
                        .filter(
                                evaluation ->
                                        evaluation.getDefense() != null
                                                && defense.getId()
                                                        .equals(evaluation.getDefense().getId()))
                        .map(evaluation -> evaluation.getLecturer().getId())
                        .collect(java.util.stream.Collectors.toSet());
        if (memberIds.isEmpty()
                || evaluatorIds.size() != componentEvaluations.size()
                || !evaluatorIds.equals(memberIds)) {
            throw new BusinessRuleException(
                    "Cannot finalize while a committee member assignment is missing.");
        }
    }

    @Transactional
    @BusinessAudit(action = "RESULT_PUBLISH", entityType = "RESULT_VERSION", entityIdArgument = 0)
    public ResultResponse publish(Long resultId, Long actorId) {
        actorAccess.manager(actorId);
        ResultVersion resultVersion =
                results.findByIdForUpdate(resultId)
                        .orElseThrow(() -> new ResourceNotFoundException("Result was not found."));
        if (resultVersion.getStatus() != ResultStatus.FINALIZED) {
            throw new BusinessRuleException("Only a finalized result can be published.");
        }

        results.findByAssignmentIdAndStatus(
                        resultVersion.getAssignment().getId(), ResultStatus.PUBLISHED)
                .filter(previous -> !previous.getId().equals(resultVersion.getId()))
                .ifPresent(
                        previous -> {
                            previous.setStatus(ResultStatus.SUPERSEDED);
                            results.saveAndFlush(previous);
                        });

        resultVersion.setStatus(ResultStatus.PUBLISHED);
        resultVersion.setPublishedBy(actorAccess.user(actorId));
        resultVersion.setPublishedAt(LocalDateTime.now(clock));
        groupMembers
                .findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(
                        resultVersion.getAssignment().getGroup().getId())
                .forEach(
                        member ->
                                notifications.publish(
                                        member.getStudent().getUser().getId(),
                                        "result-published-" + resultVersion.getId(),
                                        "Kết quả đề tài",
                                        "Kết quả đánh giá của nhóm đã được công bố.",
                                        "/results/" + resultVersion.getId()));
        results.saveAndFlush(resultVersion);
        emails.resultPublished(resultVersion);
        return result(resultVersion);
    }

    private void snapshotInputs(
            ResultVersion resultVersion,
            List<EvaluationAssignment> activeEvaluations,
            Map<Long, ScoreSheet> sourceSheets) {
        List<ResultInput> inputs =
                activeEvaluations.stream()
                        .map(
                                evaluation -> {
                                    ScoreSheet sheet = sourceSheets.get(evaluation.getId());
                                    ResultInput input = new ResultInput();
                                    input.setResult(resultVersion);
                                    input.setSheet(sheet);
                                    input.setComponent(evaluation.getComponent());
                                    input.setSheetVersion(sheet.getVersion());
                                    input.setScoreSnapshot(scoreTotal(sheet));
                                    input.setRubricSnapshot(rubricSnapshot(sheet));
                                    return input;
                                })
                        .toList();
        resultInputs.saveAllAndFlush(inputs);
    }

    private void snapshotMembers(ResultVersion resultVersion, ProjectAssignment assignment) {
        List<ResultMember> members =
                groupMembers
                        .findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(assignment.getGroup().getId())
                        .stream()
                        .map(
                                groupMember -> {
                                    ResultMember member = new ResultMember();
                                    member.setResult(resultVersion);
                                    member.setStudent(groupMember.getStudent());
                                    member.setStudentCodeSnapshot(
                                            groupMember.getStudent().getStudentCode());
                                    member.setFullNameSnapshot(
                                            groupMember.getStudent().getUser().getFullName());
                                    return member;
                                })
                        .toList();
        if (members.isEmpty()) {
            throw new BusinessRuleException(
                    "Cannot finalize a result for a group with no active members.");
        }
        resultMembers.saveAllAndFlush(members);
    }

    private BigDecimal scoreTotal(ScoreSheet sheet) {
        List<ScoreDetail> scoreDetails = details.findBySheetId(sheet.getId());
        List<com.example.qldetai.feature.evaluation.domain.RubricCriterion> requiredCriteria =
                sheet.getRubric() == null
                        ? List.of()
                        : criteria.findByRubricIdOrderBySortOrder(sheet.getRubric().getId());
        Set<Long> requiredCriterionIds =
                requiredCriteria.stream()
                        .map(com.example.qldetai.feature.evaluation.domain.RubricCriterion::getId)
                        .collect(java.util.stream.Collectors.toSet());
        Set<Long> scoredCriterionIds =
                scoreDetails.stream()
                        .map(detail -> detail.getCriterion().getId())
                        .collect(java.util.stream.Collectors.toSet());
        if (requiredCriteria.isEmpty()
                || scoreDetails.size() != requiredCriteria.size()
                || !scoredCriterionIds.equals(requiredCriterionIds)) {
            throw new BusinessRuleException(
                    "A score sheet must contain every rubric criterion before finalization.");
        }
        BigDecimal maximum =
                requiredCriteria.stream()
                        .map(
                                com.example.qldetai.feature.evaluation.domain.RubricCriterion
                                        ::getMaxScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (maximum.compareTo(BigDecimal.TEN) != 0) {
            throw new BusinessRuleException(
                    "A score sheet rubric must total exactly 10 points before finalization.");
        }
        return scoreDetails.stream()
                .map(ScoreDetail::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String rubricSnapshot(ScoreSheet sheet) {
        List<Map<String, Object>> criteriaSnapshot =
                details.findBySheetId(sheet.getId()).stream()
                        .sorted(
                                Comparator.comparingInt(
                                        detail -> detail.getCriterion().getSortOrder()))
                        .map(
                                detail -> {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("criterionId", detail.getCriterion().getId());
                                    item.put("name", detail.getCriterion().getName());
                                    item.put("description", detail.getCriterion().getDescription());
                                    item.put("maxScore", detail.getMaxScore());
                                    item.put("score", detail.getScore());
                                    item.put("comment", detail.getComment());
                                    item.put("sortOrder", detail.getCriterion().getSortOrder());
                                    return item;
                                })
                        .toList();
        try {
            return objectMapper.writeValueAsString(criteriaSnapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Unable to serialize the immutable rubric snapshot.", exception);
        }
    }

    private ResultResponse result(ResultVersion resultVersion) {
        return new ResultResponse(
                resultVersion.getId(),
                resultVersion.getAssignment().getId(),
                resultVersion.getVersionNo(),
                resultVersion.getFinalScore(),
                resultVersion.getStatus());
    }

    private ResultDetailResponse resultDetail(ResultVersion resultVersion) {
        return resultDetail(
                resultVersion,
                resultInputs.findByResultIdOrderByIdAsc(resultVersion.getId()),
                resultMembers.findByResultIdOrderByIdAsc(resultVersion.getId()));
    }

    private ResultDetailResponse resultDetail(
            ResultVersion resultVersion, List<ResultInput> inputs, List<ResultMember> members) {
        ProjectAssignment assignment = resultVersion.getAssignment();
        return new ResultDetailResponse(
                resultVersion.getId(),
                assignment.getId(),
                assignment.getGroup().getId(),
                assignment.getGroup().getName(),
                assignment.getTopic().getId(),
                assignment.getTopic().getCode(),
                assignment.getTopic().getTitle(),
                assignment.getPeriod().getId(),
                assignment.getPeriod().getName(),
                resultVersion.getVersionNo(),
                resultVersion.getFinalScore(),
                resultVersion.getStatus(),
                resultVersion.getFinalizedBy() == null
                        ? null
                        : resultVersion.getFinalizedBy().getId(),
                resultVersion.getFinalizedBy() == null
                        ? null
                        : resultVersion.getFinalizedBy().getFullName(),
                resultVersion.getFinalizedAt(),
                resultVersion.getPublishedBy() == null
                        ? null
                        : resultVersion.getPublishedBy().getId(),
                resultVersion.getPublishedBy() == null
                        ? null
                        : resultVersion.getPublishedBy().getFullName(),
                resultVersion.getPublishedAt(),
                inputs.stream()
                        .map(
                                input ->
                                        new ResultInputResponse(
                                                input.getId(),
                                                input.getSheet().getId(),
                                                input.getComponent().getId(),
                                                input.getComponent().getCode(),
                                                input.getSheetVersion(),
                                                input.getScoreSnapshot(),
                                                input.getRubricSnapshot()))
                        .toList(),
                members.stream()
                        .map(
                                member ->
                                        new ResultMemberResponse(
                                                member.getStudent().getId(),
                                                member.getStudentCodeSnapshot(),
                                                member.getFullNameSnapshot()))
                        .toList());
    }
}
