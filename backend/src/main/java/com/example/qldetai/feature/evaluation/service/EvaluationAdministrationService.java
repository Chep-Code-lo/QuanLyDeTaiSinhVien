package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import com.example.qldetai.feature.evaluation.domain.CommitteeMember;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.CommitteeStatus;
import com.example.qldetai.feature.evaluation.domain.DefenseSession;
import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationCommittee;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.domain.GradeChangeRequest;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.domain.ResultStatus;
import com.example.qldetai.feature.evaluation.domain.ResultVersion;
import com.example.qldetai.feature.evaluation.domain.ScoreDetail;
import com.example.qldetai.feature.evaluation.domain.ScoreSheet;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.AddCommitteeMemberRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CommitteeMemberResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateCommitteeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateDefenseRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateGradeChangeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseSessionResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.GradeChangeRequestResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultDetailResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ReviewGradeChangeRequest;
import com.example.qldetai.feature.evaluation.repository.CommitteeMemberRepository;
import com.example.qldetai.feature.evaluation.repository.DefenseSessionRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationCommitteeRepository;
import com.example.qldetai.feature.evaluation.repository.GradeChangeRequestRepository;
import com.example.qldetai.feature.evaluation.repository.ResultVersionRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreDetailRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationAdministrationService {

    private final EvaluationCommitteeRepository committees;
    private final CommitteeMemberRepository committeeMembers;
    private final DefenseSessionRepository defenses;
    private final RegistrationPeriodRepository periods;
    private final LecturerRepository lecturers;
    private final StudentRepository students;
    private final UserRepository users;
    private final ProjectAssignmentRepository assignments;
    private final TopicSupervisorRepository supervisors;
    private final ScoreSheetRepository sheets;
    private final ScoreDetailRepository details;
    private final EvaluationAssignmentRepository evaluations;
    private final ResultVersionRepository results;
    private final GradeChangeRequestRepository changes;
    private final GroupMemberRepository groupMembers;
    private final NotificationService notifications;
    private final Clock clock;

    public EvaluationAdministrationService(
            EvaluationCommitteeRepository committees,
            CommitteeMemberRepository committeeMembers,
            DefenseSessionRepository defenses,
            RegistrationPeriodRepository periods,
            LecturerRepository lecturers,
            StudentRepository students,
            UserRepository users,
            ProjectAssignmentRepository assignments,
            TopicSupervisorRepository supervisors,
            ScoreSheetRepository sheets,
            ScoreDetailRepository details,
            EvaluationAssignmentRepository evaluations,
            ResultVersionRepository results,
            GradeChangeRequestRepository changes,
            GroupMemberRepository groupMembers,
            NotificationService notifications,
            Clock clock) {
        this.committees = committees;
        this.committeeMembers = committeeMembers;
        this.defenses = defenses;
        this.periods = periods;
        this.lecturers = lecturers;
        this.students = students;
        this.users = users;
        this.assignments = assignments;
        this.supervisors = supervisors;
        this.sheets = sheets;
        this.details = details;
        this.evaluations = evaluations;
        this.results = results;
        this.changes = changes;
        this.groupMembers = groupMembers;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationCommitteeResponse> listCommittees(Long periodId, int page, int size) {
        Specification<EvaluationCommittee> specification = (root, query, builder) -> builder.conjunction();
        if (periodId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("period").get("id"), periodId));
        }
        return PageResponse.from(committees.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())))
                .map(this::committee));
    }

    @Transactional
    @BusinessAudit(action = "COMMITTEE_CREATE", entityType = "EVALUATION_COMMITTEE")
    public Long createCommittee(CreateCommitteeRequest request, Long actorId) {
        manager(actorId);
        RegistrationPeriod period = periods.findById(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration period was not found."));

        EvaluationCommittee committee = new EvaluationCommittee();
        committee.setPeriod(period);
        committee.setFaculty(period.getFaculty());
        committee.setName(request.name().trim());
        committee.setCreatedBy(user(actorId));
        committee.setCreatedAt(LocalDateTime.now(clock));
        return committees.save(committee).getId();
    }

    @Transactional
    @BusinessAudit(action = "COMMITTEE_MEMBER_ADD", entityType = "EVALUATION_COMMITTEE", entityIdArgument = 0)
    public void addMember(Long committeeId, AddCommitteeMemberRequest request, Long actorId) {
        manager(actorId);
        EvaluationCommittee committee = committee(committeeId);
        if (committee.getStatus() != CommitteeStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft committee can be changed.");
        }
        if (committeeMembers.findByCommitteeId(committeeId).size() >= 5) {
            throw new BusinessRuleException("A committee has at most five members.");
        }
        if (committeeMembers.findByCommitteeId(committeeId).stream().anyMatch(member ->
                member.getSlotNo() == request.slotNo()
                        || member.getLecturer().getId().equals(request.lecturerId())
                        || (member.getCommitteeRole() == request.role() && request.role() != CommitteeRole.MEMBER))) {
            throw new ConflictException("Duplicate committee slot, lecturer, or office.");
        }

        CommitteeMember member = new CommitteeMember();
        member.setCommittee(committee);
        member.setLecturer(lecturers.findById(request.lecturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer was not found.")));
        member.setSlotNo(request.slotNo());
        member.setCommitteeRole(request.role());
        committeeMembers.save(member);
    }

    @Transactional(readOnly = true)
    public PageResponse<DefenseSessionResponse> listDefenses(Long periodId, Long committeeId, int page, int size) {
        Specification<DefenseSession> specification = (root, query, builder) -> builder.conjunction();
        if (periodId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("period").get("id"), periodId));
        }
        if (committeeId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("committee").get("id"), committeeId));
        }
        return PageResponse.from(defenses.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by("startsAt").descending().and(Sort.by("id").descending())))
                .map(this::defense));
    }

    @Transactional
    @BusinessAudit(action = "DEFENSE_SCHEDULE", entityType = "DEFENSE_SESSION")
    public Long schedule(CreateDefenseRequest request, Long actorId) {
        manager(actorId);
        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessRuleException("Defense start time must be before end time.");
        }

        ProjectAssignment assignment = assignment(request.assignmentId());
        EvaluationCommittee committee = committee(request.committeeId());
        List<CommitteeMember> members = committeeMembers.findByCommitteeId(committee.getId());
        if (members.size() < 3
                || members.size() > 5
                || members.stream().noneMatch(member -> member.getCommitteeRole() == CommitteeRole.CHAIR)
                || members.stream().noneMatch(member -> member.getCommitteeRole() == CommitteeRole.SECRETARY)) {
            throw new BusinessRuleException("A committee needs 3-5 members, one chair and one secretary.");
        }
        if (defenses.hasCommitteeConflict(committee.getId(), request.startsAt(), request.endsAt())) {
            throw new ConflictException("The committee already has a defense in this time range.");
        }
        if (supervisors.findByTopicIdOrderBySlotNoAsc(assignment.getTopic().getId()).stream()
                .anyMatch(supervisor -> members.stream().anyMatch(member ->
                        member.getLecturer().getId().equals(supervisor.getLecturer().getId())))) {
            throw new BusinessRuleException("A topic supervisor cannot be on its defense committee.");
        }

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

    @Transactional(readOnly = true)
    public PageResponse<ResultDetailResponse> listResults(
            Long periodId,
            Long assignmentId,
            ResultStatus status,
            int page,
            int size,
            Long actorId) {
        manager(actorId);
        Specification<ResultVersion> specification = (root, query, builder) -> builder.conjunction();
        if (periodId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("assignment").join("period").get("id"), periodId));
        }
        if (assignmentId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("assignment").get("id"), assignmentId));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        return PageResponse.from(results.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by("finalizedAt").descending().and(Sort.by("id").descending())))
                .map(this::resultDetail));
    }

    @Transactional(readOnly = true)
    public ResultDetailResponse getResult(Long resultId, Long actorId) {
        ResultVersion resultVersion = results.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result was not found."));
        if (!isManager(actorId)) {
            Student student = students.findByUserId(actorId)
                    .orElseThrow(() -> new AccessDeniedException("You cannot view this result."));
            if (resultVersion.getStatus() != ResultStatus.PUBLISHED
                    || groupMembers.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(
                                    resultVersion.getAssignment().getGroup().getId())
                            .stream()
                            .noneMatch(member -> member.getStudent().getId().equals(student.getId()))) {
                throw new AccessDeniedException("You cannot view this result.");
            }
        }
        return resultDetail(resultVersion);
    }

    @Transactional
    @BusinessAudit(action = "RESULT_FINALIZE", entityType = "RESULT_VERSION", entityIdArgument = 0)
    public ResultResponse finalizeResult(Long assignmentId, Long actorId) {
        manager(actorId);
        ProjectAssignment assignment = assignment(assignmentId);
        if (results.findByAssignmentIdAndStatus(assignmentId, ResultStatus.PUBLISHED).isPresent()) {
            throw new BusinessRuleException("A published result cannot be finalized again.");
        }

        List<EvaluationAssignment> activeEvaluations = evaluations.findAll().stream()
                .filter(evaluation -> evaluation.getAssignment().getId().equals(assignmentId)
                        && evaluation.getStatus() == EvaluationStatus.ACTIVE)
                .toList();
        if (activeEvaluations.isEmpty()) {
            throw new BusinessRuleException("This assignment has no evaluation assignments.");
        }

        Map<Long, Long> submittedCounts = activeEvaluations.stream()
                .filter(evaluation -> sheets.findByEvaluationId(evaluation.getId())
                        .map(sheet -> sheet.getStatus() == ScoreSheetStatus.SUBMITTED)
                        .orElse(false))
                .collect(java.util.stream.Collectors.groupingBy(
                        evaluation -> evaluation.getComponent().getId(),
                        java.util.stream.Collectors.counting()));

        for (EvaluationAssignment evaluation : activeEvaluations) {
            if (submittedCounts.getOrDefault(evaluation.getComponent().getId(), 0L)
                    < evaluation.getComponent().getExpectedEvaluators()) {
                throw new BusinessRuleException("Cannot finalize while a required score sheet is missing.");
            }
        }

        List<BigDecimal> totals = activeEvaluations.stream()
                .map(evaluation -> sheets.findByEvaluationId(evaluation.getId()).orElseThrow())
                .map(sheet -> details.findBySheetId(sheet.getId()).stream()
                        .map(ScoreDetail::getScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .toList();

        BigDecimal finalScore = totals.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(totals.size()), 2, RoundingMode.HALF_UP);

        ResultVersion resultVersion = new ResultVersion();
        resultVersion.setAssignment(assignment);
        resultVersion.setVersionNo((int) results.countByAssignmentId(assignmentId) + 1);
        resultVersion.setFinalScore(finalScore);
        resultVersion.setFinalizedBy(user(actorId));
        resultVersion.setFinalizedAt(LocalDateTime.now(clock));
        activeEvaluations.forEach(evaluation ->
                sheets.findByEvaluationId(evaluation.getId()).ifPresent(sheet -> sheet.setStatus(ScoreSheetStatus.LOCKED)));
        resultVersion = results.save(resultVersion);
        return result(resultVersion);
    }

    @Transactional
    @BusinessAudit(action = "RESULT_PUBLISH", entityType = "RESULT_VERSION", entityIdArgument = 0)
    public ResultResponse publish(Long resultId, Long actorId) {
        manager(actorId);
        ResultVersion resultVersion = results.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result was not found."));
        if (resultVersion.getStatus() != ResultStatus.FINALIZED) {
            throw new BusinessRuleException("Only a finalized result can be published.");
        }

        resultVersion.setStatus(ResultStatus.PUBLISHED);
        resultVersion.setPublishedBy(user(actorId));
        resultVersion.setPublishedAt(LocalDateTime.now(clock));
        groupMembers.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(resultVersion.getAssignment().getGroup().getId())
                .forEach(member -> notifications.publish(
                        member.getStudent().getUser().getId(),
                        "result-published-" + resultVersion.getId(),
                        "Kết quả đề tài",
                        "Kết quả đánh giá của nhóm đã được công bố.",
                        "/results/" + resultVersion.getId()));
        return result(resultVersion);
    }

    @Transactional(readOnly = true)
    public PageResponse<GradeChangeRequestResponse> listChanges(
            GradeChangeStatus status,
            Long sheetId,
            int page,
            int size,
            Long actorId) {
        manager(actorId);
        Specification<GradeChangeRequest> specification = (root, query, builder) -> builder.conjunction();
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (sheetId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.join("sheet").get("id"), sheetId));
        }
        return PageResponse.from(changes.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())))
                .map(this::gradeChange));
    }

    @Transactional
    public Long requestChange(Long sheetId, CreateGradeChangeRequest request, Long actorId) {
        ScoreSheet sheet = sheets.findById(sheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Score sheet was not found."));
        if (!sheet.getEvaluation().getLecturer().getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("Only the evaluator can request a score change.");
        }
        if (sheet.getStatus() != ScoreSheetStatus.LOCKED
                || changes.existsBySheetIdAndStatusIn(sheetId, List.of(GradeChangeStatus.PENDING, GradeChangeStatus.APPROVED))) {
            throw new BusinessRuleException("This score sheet cannot receive another change request.");
        }

        GradeChangeRequest changeRequest = new GradeChangeRequest();
        changeRequest.setSheet(sheet);
        changeRequest.setRequestedBy(user(actorId));
        changeRequest.setReason(request.reason().trim());
        changeRequest.setCreatedAt(LocalDateTime.now(clock));
        return changes.save(changeRequest).getId();
    }

    @Transactional
    public void reviewChange(Long id, ReviewGradeChangeRequest request, Long actorId) {
        manager(actorId);
        GradeChangeRequest changeRequest = changes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade change request was not found."));
        if (changeRequest.getStatus() != GradeChangeStatus.PENDING) {
            throw new BusinessRuleException("Only a pending request may be reviewed.");
        }

        changeRequest.setReviewedBy(user(actorId));
        changeRequest.setReviewedAt(LocalDateTime.now(clock));
        changeRequest.setReviewComment(request.comment());
        if (Boolean.TRUE.equals(request.approved())) {
            changeRequest.setStatus(GradeChangeStatus.APPROVED);
            changeRequest.getSheet().setStatus(ScoreSheetStatus.DRAFT);
            changeRequest.getSheet().setSubmittedAt(null);
        } else {
            changeRequest.setStatus(GradeChangeStatus.REJECTED);
        }
    }

    private boolean isManager(Long actorId) {
        return users.findWithRolesById(actorId)
                .map(user -> user.getUserRoles().stream()
                        .anyMatch(role -> role.getRole().getCode() == RoleCode.ADMIN
                                || role.getRole().getCode() == RoleCode.LECTURER))
                .orElse(false);
    }

    private void manager(Long actorId) {
        if (!isManager(actorId)) {
            throw new AccessDeniedException("Only lecturers or administrators can manage evaluation.");
        }
    }

    private User user(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
    }

    private EvaluationCommittee committee(Long committeeId) {
        return committees.findById(committeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Committee was not found."));
    }

    private ProjectAssignment assignment(Long assignmentId) {
        return assignments.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Project assignment was not found."));
    }

    private EvaluationCommitteeResponse committee(EvaluationCommittee committee) {
        List<CommitteeMemberResponse> members = committeeMembers.findByCommitteeId(committee.getId()).stream()
                .sorted(Comparator.comparingInt(CommitteeMember::getSlotNo))
                .map(member -> new CommitteeMemberResponse(
                        member.getId(),
                        member.getLecturer().getId(),
                        member.getLecturer().getLecturerCode(),
                        member.getLecturer().getUser().getFullName(),
                        member.getSlotNo(),
                        member.getCommitteeRole()))
                .toList();
        return new EvaluationCommitteeResponse(
                committee.getId(),
                committee.getPeriod().getId(),
                committee.getPeriod().getName(),
                committee.getFaculty().getId(),
                committee.getFaculty().getName(),
                committee.getName(),
                committee.getStatus(),
                committee.getCreatedBy().getId(),
                committee.getCreatedBy().getFullName(),
                committee.getCreatedAt(),
                committee.getVersion(),
                members);
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

    private ResultResponse result(ResultVersion resultVersion) {
        return new ResultResponse(
                resultVersion.getId(),
                resultVersion.getAssignment().getId(),
                resultVersion.getVersionNo(),
                resultVersion.getFinalScore(),
                resultVersion.getStatus());
    }

    private ResultDetailResponse resultDetail(ResultVersion resultVersion) {
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
                resultVersion.getFinalizedBy() == null ? null : resultVersion.getFinalizedBy().getId(),
                resultVersion.getFinalizedBy() == null ? null : resultVersion.getFinalizedBy().getFullName(),
                resultVersion.getFinalizedAt(),
                resultVersion.getPublishedBy() == null ? null : resultVersion.getPublishedBy().getId(),
                resultVersion.getPublishedBy() == null ? null : resultVersion.getPublishedBy().getFullName(),
                resultVersion.getPublishedAt());
    }

    private GradeChangeRequestResponse gradeChange(GradeChangeRequest changeRequest) {
        ScoreSheet sheet = changeRequest.getSheet();
        return new GradeChangeRequestResponse(
                changeRequest.getId(),
                sheet.getId(),
                sheet.getEvaluation().getId(),
                sheet.getEvaluation().getAssignment().getId(),
                changeRequest.getRequestedBy().getId(),
                changeRequest.getRequestedBy().getFullName(),
                changeRequest.getReason(),
                changeRequest.getStatus(),
                changeRequest.getReviewedBy() == null ? null : changeRequest.getReviewedBy().getId(),
                changeRequest.getReviewedBy() == null ? null : changeRequest.getReviewedBy().getFullName(),
                changeRequest.getReviewComment(),
                changeRequest.getCreatedAt(),
                changeRequest.getReviewedAt(),
                changeRequest.getAppliedAt());
    }
}
