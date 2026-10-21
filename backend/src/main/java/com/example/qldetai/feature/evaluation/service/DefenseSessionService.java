package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponent;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.domain.CommitteeMember;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.CommitteeStatus;
import com.example.qldetai.feature.evaluation.domain.DefenseSession;
import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationCommittee;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.domain.ScoreDetail;
import com.example.qldetai.feature.evaluation.domain.ScoreSheet;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateDefenseRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseSessionResponse;
import com.example.qldetai.feature.evaluation.repository.AssessmentComponentRepository;
import com.example.qldetai.feature.evaluation.repository.CommitteeMemberRepository;
import com.example.qldetai.feature.evaluation.repository.DefenseSessionRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationCommitteeRepository;
import com.example.qldetai.feature.evaluation.repository.RubricCriterionRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreDetailRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefenseSessionService {

    private final EvaluationCommitteeRepository committees;
    private final CommitteeMemberRepository committeeMembers;
    private final AssessmentComponentRepository components;
    private final DefenseSessionRepository defenses;
    private final ProjectAssignmentRepository assignments;
    private final TopicSupervisorRepository supervisors;
    private final ScoreSheetRepository sheets;
    private final ScoreDetailRepository details;
    private final RubricCriterionRepository criteria;
    private final EvaluationAssignmentRepository evaluations;
    private final EvaluationActorAccess actorAccess;


    @Transactional(readOnly = true)
    public PageResponse<DefenseSessionResponse> listDefenses(
            Long periodId, Long committeeId, int page, int size) {
        Specification<DefenseSession> specification =
                (root, query, builder) -> builder.conjunction();
        if (periodId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("period").get("id"), periodId));
        }
        if (committeeId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("committee").get("id"), committeeId));
        }
        return PageResponse.from(
                defenses.findAll(
                                specification,
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("startsAt")
                                                .descending()
                                                .and(Sort.by("id").descending())))
                        .map(this::defense));
    }

    @Transactional(readOnly = true)
    public List<DefenseSessionResponse> listDefensesForStudent(Long userId) {
        return defenses.findByCurrentStudentUserId(userId).stream().map(this::defense).toList();
    }

    @Transactional
    @BusinessAudit(action = "DEFENSE_SCHEDULE", entityType = "DEFENSE_SESSION")
    public Long schedule(CreateDefenseRequest request, Long actorId) {
        actorAccess.manager(actorId);
        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessRuleException("Defense start time must be before end time.");
        }

        ProjectAssignment assignment = assignment(request.assignmentId());
        EvaluationCommittee committee = committee(request.committeeId());
        if (!assignment.getPeriod().getId().equals(committee.getPeriod().getId())) {
            throw new BusinessRuleException(
                    "The committee and project assignment must belong to the same registration"
                        + " period.");
        }
        List<CommitteeMember> members = committeeMembers.findByCommitteeIdForUpdate(committee.getId());
        int minMembers = assignment.getPeriod().getMinCommitteeMembers();
        int maxMembers = assignment.getPeriod().getMaxCommitteeMembers();
        if (minMembers < 3 || maxMembers > 5 || members.size() < 3 || members.size() > 5
                || members.size() < minMembers
                || members.size() > maxMembers
                || members.stream()
                        .noneMatch(member -> member.getCommitteeRole() == CommitteeRole.CHAIR)
                || members.stream()
                        .noneMatch(
                                member -> member.getCommitteeRole() == CommitteeRole.SECRETARY)) {
            throw new BusinessRuleException(
                    "A committee needs the configured number of members, one chair and one"
                        + " secretary.");
        }
        if (!defenses.findConflictsForUpdate(
                committee.getId(), request.startsAt(), request.endsAt()).isEmpty()) {
            throw new ConflictException("The committee already has a defense in this time range.");
        }
        if (supervisors.findByTopicIdOrderBySlotNoAsc(assignment.getTopic().getId()).stream()
                .anyMatch(
                        supervisor ->
                                members.stream()
                                        .anyMatch(
                                                member ->
                                                        member.getLecturer()
                                                                .getId()
                                                                .equals(
                                                                        supervisor
                                                                                .getLecturer()
                                                                                .getId())))) {
            throw new BusinessRuleException(
                    "A topic supervisor cannot be on its defense committee.");
        }
        ensureReviewCompleteBeforeCommittee(assignment);

        DefenseSession defense = new DefenseSession();
        defense.setAssignment(assignment);
        defense.setCommittee(committee);
        defense.setPeriod(assignment.getPeriod());
        defense.setLocation(request.location().trim());
        defense.setStartsAt(request.startsAt());
        defense.setEndsAt(request.endsAt());
        committee.setStatus(CommitteeStatus.CONFIRMED);
        return defenses.save(defense).getId();
    }

    private void ensureReviewCompleteBeforeCommittee(ProjectAssignment assignment) {
        List<AssessmentComponent> reviewComponents =
                components.findByPeriodIdOrderByCodeAsc(assignment.getPeriod().getId()).stream()
                        .filter(
                                component ->
                                        component.getComponentType()
                                                == AssessmentComponentType.REVIEW)
                        .toList();
        for (AssessmentComponent reviewComponent : reviewComponents) {
            if (!"LOCKED".equals(reviewComponent.getStatus())) {
                throw new BusinessRuleException(
                        "Lock every review component before scheduling a defense.");
            }
            List<EvaluationAssignment> reviewerAssignments =
                    evaluations.findByAssignmentIdAndComponentIdAndStatus(
                            assignment.getId(), reviewComponent.getId(), EvaluationStatus.ACTIVE);
            if (reviewerAssignments.size() != reviewComponent.getExpectedEvaluators()
                    || reviewerAssignments.stream()
                            .anyMatch(evaluation -> !hasCompletedReviewSheet(evaluation))) {
                throw new BusinessRuleException(
                        "Complete every reviewer assignment and submitted review score before"
                            + " scheduling a defense.");
            }
        }
    }

    private boolean hasCompletedReviewSheet(EvaluationAssignment evaluation) {
        return sheets.findByEvaluationId(evaluation.getId())
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
                        .map(com.example.qldetai.feature.evaluation.domain.RubricCriterion::getId)
                        .collect(java.util.stream.Collectors.toSet());
        List<ScoreDetail> scoreDetails = details.findBySheetId(sheet.getId());
        Set<Long> scoredCriterionIds =
                scoreDetails.stream()
                        .map(detail -> detail.getCriterion().getId())
                        .collect(java.util.stream.Collectors.toSet());
        return !requiredCriterionIds.isEmpty()
                && scoreDetails.size() == requiredCriterionIds.size()
                && scoredCriterionIds.equals(requiredCriterionIds);
    }

    private EvaluationCommittee committee(Long committeeId) {
        return committees
                .findByIdForUpdate(committeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Committee was not found."));
    }

    private ProjectAssignment assignment(Long assignmentId) {
        return assignments
                .findByIdForUpdate(assignmentId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Project assignment was not found."));
    }

    private DefenseSessionResponse defense(DefenseSession defense) {
        return new DefenseSessionResponse(
                defense.getId(),
                defense.getAssignment().getId(),
                defense.getAssignment().getGroup().getId(),
                defense.getAssignment().getGroup().getName(),
                defense.getAssignment().getTopic().getId(),
                defense.getAssignment().getTopic().getCode(),
                defense.getAssignment().getTopic().getTitle(),
                defense.getCommittee().getId(),
                defense.getCommittee().getName(),
                defense.getPeriod().getId(),
                defense.getPeriod().getName(),
                defense.getLocation(),
                defense.getStartsAt(),
                defense.getEndsAt(),
                defense.getStatus(),
                defense.getVersion());
    }
}
