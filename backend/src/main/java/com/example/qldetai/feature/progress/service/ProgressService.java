package com.example.qldetai.feature.progress.service;

import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.exception.*;
import com.example.qldetai.feature.academic.service.AcademicAuthorization;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.domain.Milestone;
import com.example.qldetai.feature.progress.domain.MilestoneStatus;
import com.example.qldetai.feature.progress.domain.ReportFeedback;
import com.example.qldetai.feature.progress.domain.ReportSubmission;
import com.example.qldetai.feature.progress.domain.StoredFile;
import com.example.qldetai.feature.progress.domain.StoredFileStatus;
import com.example.qldetai.feature.progress.dto.ProgressDtos.*;
import com.example.qldetai.feature.progress.repository.MilestoneRepository;
import com.example.qldetai.feature.progress.repository.ReportFeedbackRepository;
import com.example.qldetai.feature.progress.repository.ReportSubmissionRepository;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgressService {
    private final MilestoneRepository milestones;
    private final ReportSubmissionRepository submissions;
    private final ReportFeedbackRepository feedback;
    private final StoredFileRepository files;
    private final ProjectAssignmentRepository assignments;
    private final StudentRepository students;
    private final LecturerRepository lecturers;
    private final UserRepository users;
    private final GroupMemberRepository members;
    private final TopicSupervisorRepository supervisors;
    private final NotificationService notifications;
    private final AcademicAuthorization academicAuthorization;
    private final Clock clock;

    @Transactional
    @BusinessAudit(action = "MILESTONE_CREATE", entityType = "MILESTONE")
    public MilestoneResponse create(CreateMilestoneRequest request, Long actorId) {
        ProjectAssignment a = assignment(request.assignmentId());
        requireManager(actorId);
        if (a.getStatus() != AssignmentStatus.IN_PROGRESS)
            throw new BusinessRuleException(
                    "Milestones can only be created for an in-progress assignment.");
        Milestone m = new Milestone();
        m.setAssignment(a);
        m.setTitle(request.title().trim());
        m.setMilestoneType(request.milestoneType());
        m.setDescription(request.description().trim());
        m.setDueAt(request.dueAt());
        m.setCreatedBy(user(actorId));
        return response(milestones.save(m));
    }

    @Transactional
    @BusinessAudit(action = "MILESTONE_UPDATE", entityType = "MILESTONE", entityIdArgument = 0)
    public MilestoneResponse update(Long milestoneId, UpdateMilestoneRequest request) {
        Milestone milestone = milestone(milestoneId);
        if (milestone.getAssignment().getStatus() != AssignmentStatus.IN_PROGRESS)
            throw new BusinessRuleException(
                    "Milestones can only be updated for an in-progress assignment.");
        if (milestone.getStatus() == MilestoneStatus.ACCEPTED)
            throw new BusinessRuleException("An accepted milestone cannot be updated.");
        if (milestone.getVersion() != request.version())
            throw new ConflictException(
                    "The milestone was updated by another user. Refresh and try again.");
        milestone.setTitle(request.title().trim());
        milestone.setMilestoneType(request.milestoneType());
        milestone.setDescription(request.description().trim());
        milestone.setDueAt(request.dueAt());
        return response(milestones.saveAndFlush(milestone));
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(Long assignmentId, Long actorId) {
        ProjectAssignment a = assignment(assignmentId);
        requireAssignmentParticipant(a, actorId);
        return responses(milestones.findByAssignmentIdOrderByDueAtAsc(assignmentId));
    }

    @Transactional
    @BusinessAudit(action = "REPORT_SUBMIT", entityType = "REPORT_SUBMISSION", entityIdArgument = 0)
    public SubmissionResponse submit(Long milestoneId, SubmitReportRequest request, Long actorId) {
        Milestone m = milestone(milestoneId);
        Student student =
                students.findByUserId(actorId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Only the group leader can submit a report."));
        requireLeader(m.getAssignment(), student.getId());
        if (m.getStatus() == MilestoneStatus.ACCEPTED)
            throw new BusinessRuleException("An accepted milestone cannot receive another report.");
        StoredFile file =
                files.findById(request.fileId())
                        .orElseThrow(() -> new ResourceNotFoundException("File was not found."));
        if (file.getStatus() != StoredFileStatus.READY
                || !file.getUploadedBy().getId().equals(actorId))
            throw new AccessDeniedException("The submitted file must be your ready file.");
        ReportSubmission s = new ReportSubmission();
        s.setMilestone(m);
        s.setFile(file);
        s.setSubmittedBy(student);
        s.setNote(request.note());
        s.setSubmittedAt(LocalDateTime.now(clock));
        s.setVersionNo(
                submissions
                        .findFirstByMilestoneIdOrderByVersionNoDesc(milestoneId)
                        .map(latest -> Math.addExact(latest.getVersionNo(), 1))
                        .orElse(1));
        m.setStatus(MilestoneStatus.SUBMITTED);
        s = submissions.save(s);
        notifySupervisors(m, "Nhóm đã nộp báo cáo: " + m.getTitle());
        return submission(s);
    }

    @Transactional
    @BusinessAudit(
            action = "REPORT_FEEDBACK",
            entityType = "REPORT_SUBMISSION",
            entityIdArgument = 0)
    public SubmissionResponse feedback(Long submissionId, FeedbackRequest request, Long actorId) {
        ReportSubmission s = submissionEntity(submissionId);
        milestone(s.getMilestone().getId());
        Lecturer lecturer =
                lecturers
                        .findByUserId(actorId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Only lecturers can give feedback."));
        if (!isSupervisor(s.getMilestone().getAssignment(), lecturer.getId()))
            throw new AccessDeniedException("Only this topic's supervisor may give feedback.");
        ReportFeedback f = new ReportFeedback();
        f.setSubmission(s);
        f.setLecturer(lecturer);
        f.setContent(request.content().trim());
        feedback.save(f);
        s.getMilestone()
                .setStatus(
                        Boolean.TRUE.equals(request.accepted())
                                ? MilestoneStatus.ACCEPTED
                                : MilestoneStatus.REVISION_REQUIRED);
        notifyGroup(
                s.getMilestone().getAssignment(),
                "Báo cáo '" + s.getMilestone().getTitle() + "' đã có phản hồi.");
        return submission(s);
    }

    private void requireAssignmentParticipant(ProjectAssignment a, Long userId) {
        if (hasAdminRole(userId)
                || isSupervisor(a, lecturerId(userId))
                || academicAuthorization.canManageDepartment(
                        a.getTopic().getDepartment().getId(), userId)) return;
        Student s =
                students.findByUserId(userId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "You cannot view this assignment."));
        if (members.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(a.getGroup().getId()).stream()
                .noneMatch(x -> x.getStudent().getId().equals(s.getId())))
            throw new AccessDeniedException("You cannot view this assignment.");
    }

    private void requireLeader(ProjectAssignment a, Long studentId) {
        boolean leader =
                members.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(a.getGroup().getId()).stream()
                        .anyMatch(
                                x ->
                                        x.getStudent().getId().equals(studentId)
                                                && x.getMemberRole() == GroupMemberRole.LEADER);
        if (!leader) throw new AccessDeniedException("Only the group leader can submit a report.");
    }

    private boolean isSupervisor(ProjectAssignment a, Long lecturerId) {
        return lecturerId != null
                && supervisors.findByTopicIdOrderBySlotNoAsc(a.getTopic().getId()).stream()
                        .anyMatch(s -> s.getLecturer().getId().equals(lecturerId));
    }

    private Long lecturerId(Long userId) {
        return lecturers.findByUserId(userId).map(Lecturer::getId).orElse(null);
    }

    private boolean hasAdminRole(Long id) {
        return users.findWithRolesById(id)
                .map(
                        u ->
                                u.getUserRoles().stream()
                                        .anyMatch(r -> r.getRole().getCode() == RoleCode.ADMIN))
                .orElse(false);
    }

    private void requireManager(Long id) {
        if (!hasAdminRole(id) && lecturers.findByUserId(id).isEmpty())
            throw new AccessDeniedException(
                    "Only lecturers or administrators can manage milestones.");
    }

    private ProjectAssignment assignment(Long id) {
        return assignments
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Project assignment was not found."));
    }

    private Milestone milestone(Long id) {
        return milestones
                .findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone was not found."));
    }

    private ReportSubmission submissionEntity(Long id) {
        return submissions
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Report submission was not found."));
    }

    private User user(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
    }

    private void notifySupervisors(Milestone m, String message) {
        supervisors
                .findByTopicIdOrderBySlotNoAsc(m.getAssignment().getTopic().getId())
                .forEach(
                        x ->
                                notifications.publish(
                                        x.getLecturer().getUser().getId(),
                                        "report-" + m.getId() + "-" + m.getStatus(),
                                        "Cập nhật báo cáo",
                                        message,
                                        "/milestones/" + m.getId()));
    }

    private void notifyGroup(ProjectAssignment a, String message) {
        members.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(a.getGroup().getId())
                .forEach(
                        x ->
                                notifications.publish(
                                        x.getStudent().getUser().getId(),
                                        "report-feedback-"
                                                + a.getId()
                                                + "-"
                                                + System.nanoTime()
                                                + "-"
                                                + x.getId(),
                                        "Phản hồi báo cáo",
                                        message,
                                        "/project-assignments/" + a.getId()));
    }

    private List<MilestoneResponse> responses(List<Milestone> milestoneRows) {
        if (milestoneRows.isEmpty()) return List.of();
        var reportRows =
                submissions.findByMilestoneIdInOrderByVersionNoDesc(
                        milestoneRows.stream().map(Milestone::getId).toList());
        var byMilestone =
                reportRows.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        row -> row.getMilestone().getId()));
        java.util.Map<Long, List<ReportFeedback>> byReport =
                reportRows.isEmpty()
                        ? java.util.Map.of()
                        : feedback
                                .findBySubmissionIdInOrderByCreatedAtAsc(
                                        reportRows.stream().map(ReportSubmission::getId).toList())
                                .stream()
                                .collect(
                                        java.util.stream.Collectors.groupingBy(
                                                row -> row.getSubmission().getId()));
        return milestoneRows.stream()
                .map(
                        m ->
                                new MilestoneResponse(
                                        m.getId(),
                                        m.getAssignment().getId(),
                                        m.getTitle(),
                                        m.getMilestoneType(),
                                        m.getDescription(),
                                        m.getDueAt(),
                                        m.getStatus(),
                                        m.getVersion(),
                                        byMilestone.getOrDefault(m.getId(), List.of()).stream()
                                                .map(
                                                        report ->
                                                                submission(
                                                                        report,
                                                                        byReport.getOrDefault(
                                                                                report.getId(),
                                                                                List.of())))
                                                .toList()))
                .toList();
    }

    private MilestoneResponse response(Milestone m) {
        return new MilestoneResponse(
                m.getId(),
                m.getAssignment().getId(),
                m.getTitle(),
                m.getMilestoneType(),
                m.getDescription(),
                m.getDueAt(),
                m.getStatus(),
                m.getVersion(),
                submissions.findByMilestoneIdOrderByVersionNoDesc(m.getId()).stream()
                        .map(this::submission)
                        .toList());
    }

    private SubmissionResponse submission(ReportSubmission s) {
        return submission(s, feedback.findBySubmissionIdOrderByCreatedAtAsc(s.getId()));
    }

    private SubmissionResponse submission(ReportSubmission s, List<ReportFeedback> feedbackRows) {
        StoredFile f = s.getFile();
        return new SubmissionResponse(
                s.getId(),
                s.getVersionNo(),
                new FileResponse(
                        f.getId(),
                        f.getOriginalName(),
                        f.getMimeType(),
                        f.getSizeBytes(),
                        f.getSha256()),
                s.getSubmittedBy().getId(),
                s.getSubmittedBy().getUser().getFullName(),
                s.getNote(),
                s.getSubmittedAt(),
                feedbackRows.stream()
                        .map(
                                x ->
                                        new FeedbackResponse(
                                                x.getId(),
                                                x.getLecturer().getId(),
                                                x.getLecturer().getUser().getFullName(),
                                                x.getContent(),
                                                x.getCreatedAt()))
                        .toList());
    }
}
