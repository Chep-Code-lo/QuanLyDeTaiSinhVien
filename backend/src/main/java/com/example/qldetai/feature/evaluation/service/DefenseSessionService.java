package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.common.time.VietnamTime;
import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
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
import com.example.qldetai.feature.evaluation.repository.DefenseScheduleGuardRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationCommitteeRepository;
import com.example.qldetai.feature.evaluation.repository.RubricCriterionRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreDetailRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;

import org.springframework.beans.factory.annotation.Value;
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

    private final EvaluationReadScope readScope;
    private final EvaluationCommitteeRepository committees;
    private final CommitteeMemberRepository committeeMembers;
    private final AssessmentComponentRepository components;
    private final DefenseSessionRepository defenses;
    private final DefenseScheduleGuardRepository scheduleGuard;
    private final ProjectAssignmentRepository assignments;
    private final TopicSupervisorRepository supervisors;
    private final ScoreSheetRepository sheets;
    private final ScoreDetailRepository details;
    private final RubricCriterionRepository criteria;
    private final EvaluationAssignmentRepository evaluations;
    private final EvaluationActorAccess actorAccess;
    private final GroupMemberRepository groupMembers;
    private final NotificationService notifications;

    @Value("${app.email.display-time-zone:Asia/Ho_Chi_Minh}")
    private String displayTimeZone;


    @Transactional(readOnly = true)
    public PageResponse<DefenseSessionResponse> listDefenses(Long periodId, Long committeeId, int page, int size) {
        return listDefenses(periodId, committeeId, page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<DefenseSessionResponse> listDefenses(Long periodId, Long committeeId, int page, int size, Long actorId) {
        Specification<DefenseSession> specification = readScope.defenses(actorId);
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
        // MySQL DATETIME(6) and JPA timestamp(6) cannot preserve nanoseconds.
        // Compare and persist the same precision, especially at adjacent-session boundaries.
        request = new CreateDefenseRequest(request.assignmentId(), request.committeeId(), request.location(),
                request.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                request.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessRuleException("Defense start time must be before end time.");
        }

        // Acquire before loading project/committee rows. Hold until transaction commit.
        // Conflict queries below are locking/current reads under MySQL REPEATABLE READ.
        scheduleGuard.lockScheduling().orElseThrow(() ->
                new IllegalStateException("Defense scheduling migration has not been applied."));

        ProjectAssignment assignment = assignment(request.assignmentId());
        EvaluationCommittee committee = committee(request.committeeId());
        if (committee.getStatus() == CommitteeStatus.COMPLETED) {
            throw new BusinessRuleException("Hội đồng đã hoàn tất, không thể xếp thêm lịch bảo vệ.");
        }
        if (defenses.findByAssignmentIdForUpdate(assignment.getId()).isPresent()) {
            throw new ConflictException("Đề tài đã có phiên bảo vệ. Không thể tạo thêm phiên cho cùng đề tài.");
        }
        if (!assignment.getPeriod().getId().equals(committee.getPeriod().getId())) {
            throw new BusinessRuleException(
                    "The committee and project assignment must belong to the same registration"
                        + " period.");
        }
        validateDefenseDate(assignment.getPeriod(), request);
        List<CommitteeMember> members = committeeMembers.findByCommitteeIdForUpdate(committee.getId());
        ensureEligible(assignment, committee, members);
        if (!defenses.findConflictsForUpdate(
                committee.getId(), request.startsAt(), request.endsAt()).isEmpty()) {
            throw new ConflictException("The committee already has a defense in this time range.");
        }
        if (!defenses.findRoomConflictsForUpdate(DefenseLocation.key(request.location()),
                DefenseLocation.NORMALIZATION_PATTERN, request.startsAt(), request.endsAt()).isEmpty()) {
            throw new ConflictException("Phòng đã có lịch bảo vệ trong khoảng thời gian này. Vui lòng chọn phòng hoặc thời gian khác.");
        }
        if (!defenses.findLecturerConflictsForUpdate(
                members.stream().map(member -> member.getLecturer().getId()).toList(),
                request.startsAt(), request.endsAt()).isEmpty()) {
            throw new ConflictException("Có giảng viên trong hội đồng đã có lịch bảo vệ trùng giờ. Vui lòng chọn thời gian khác.");
        }

        DefenseSession defense = new DefenseSession();
        defense.setAssignment(assignment);
        defense.setCommittee(committee);
        defense.setPeriod(assignment.getPeriod());
        defense.setLocation(request.location().trim());
        defense.setStartsAt(request.startsAt());
        defense.setEndsAt(request.endsAt());
        committee.setStatus(CommitteeStatus.CONFIRMED);
        defense = defenses.save(defense);
        notifyStudents(defense);
        return defense.getId();
    }

    private void validateDefenseDate(RegistrationPeriod period, CreateDefenseRequest request) {
        if (period.getProjectType() != ProjectType.GRADUATION) return;
        var date = period.getDefenseDate();
        if (date == null) {
            throw new BusinessRuleException("Đợt KLTN chưa cấu hình ngày bảo vệ. Vui lòng cập nhật cấu hình đợt trước khi xếp lịch.");
        }
        if (!date.equals(VietnamTime.dateOf(request.startsAt()))
                || !date.equals(VietnamTime.dateOf(request.endsAt()))) {
            throw new BusinessRuleException("Phiên bảo vệ phải diễn ra vào ngày "
                    + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " theo cấu hình đợt KLTN.");
        }
    }

    private void notifyStudents(DefenseSession defense) {
        var formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        var zone = java.time.ZoneId.of(displayTimeZone);
        var startsAt = defense.getStartsAt().atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(zone);
        var endsAt = defense.getEndsAt().atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(zone);
        String content = "Đề tài " + defense.getAssignment().getTopic().getCode() + " - "
                + defense.getAssignment().getTopic().getTitle() + " đã được xếp lịch bảo vệ.\n"
                + "Địa điểm: " + defense.getLocation() + ".\n"
                + "Bắt đầu: " + formatter.format(startsAt) + ". Kết thúc: " + formatter.format(endsAt)
                + " (" + zone.getId() + ").";
        groupMembers.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(defense.getAssignment().getGroup().getId())
                .forEach(member -> notifications.publish(
                        member.getStudent().getUser().getId(),
                        "defense-scheduled-" + defense.getId(),
                        "Lịch bảo vệ đề tài",
                        content,
                        "/results"));
    }

    @Transactional(readOnly = true)
    public com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseEligibilityResponse eligibility(
            Long assignmentId, Long committeeId, Long actorId) {
        actorAccess.manager(actorId);
        var assignment = assignments.findById(assignmentId).orElseThrow(() -> new ResourceNotFoundException("Project assignment was not found."));
        var committee = committees.findById(committeeId).orElseThrow(() -> new ResourceNotFoundException("Committee was not found."));
        try {
            if (!assignment.getPeriod().getId().equals(committee.getPeriod().getId())) {
                throw new BusinessRuleException("The committee and project assignment must belong to the same registration period.");
            }
            if (committee.getStatus() == CommitteeStatus.COMPLETED || defenses.findByAssignmentId(assignmentId).isPresent()) {
                throw new BusinessRuleException("Project or committee already has a completed defense configuration.");
            }
            ensureEligible(assignment, committee, committeeMembers.findByCommitteeId(committeeId));
            return new com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseEligibilityResponse(true, null);
        } catch (BusinessRuleException exception) {
            return new com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseEligibilityResponse(false, exception.getMessage());
        }
    }

    private void ensureEligible(ProjectAssignment assignment, EvaluationCommittee committee, List<CommitteeMember> members) {
        int minMembers = assignment.getPeriod().getMinCommitteeMembers();
        int maxMembers = assignment.getPeriod().getMaxCommitteeMembers();
        if (minMembers < 3 || maxMembers > 5 || members.size() < 3 || members.size() > 5
                || members.size() < minMembers
                || members.size() > maxMembers
                || members.stream().filter(member -> member.getCommitteeRole() == CommitteeRole.CHAIR).count() != 1
                || members.stream().filter(member -> member.getCommitteeRole() == CommitteeRole.SECRETARY).count() != 1
                || members.stream().map(member -> member.getLecturer().getId()).distinct().count() != members.size()) {
            throw new BusinessRuleException(
                    "A committee needs the configured number of members, one chair and one"
                        + " secretary.");
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
            if (reviewerAssignments.isEmpty()
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
