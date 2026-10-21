package com.example.qldetai.feature.progress.service;

import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.email.EmailNotificationEventService;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.progress.domain.Milestone;
import com.example.qldetai.feature.progress.domain.MilestoneStatus;
import com.example.qldetai.feature.progress.dto.ProgressDtos.MilestoneReminderResponse;
import com.example.qldetai.feature.progress.repository.MilestoneRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MilestoneReminderService {

    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final MilestoneRepository milestoneRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final NotificationService notificationService;
    private final EmailNotificationEventService emailNotificationEventService;
    private final Clock clock;

    @Transactional
    @BusinessAudit(action = "MILESTONE_REMINDER_SEND", entityType = "PROJECT_ASSIGNMENT", entityIdArgument = 0)
    public MilestoneReminderResponse send(Long assignmentId) {
        ProjectAssignment assignment = projectAssignmentRepository
                .findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Project assignment was not found."));
        if (assignment.getStatus() != AssignmentStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Deadline reminders can only be sent for an in-progress assignment.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<Milestone> pendingMilestones = milestoneRepository
                .findByAssignmentIdOrderByDueAtAsc(assignmentId)
                .stream()
                .filter(milestone -> milestone.getStatus() == MilestoneStatus.OPEN)
                .filter(milestone -> milestone.getDueAt().isAfter(now))
                .toList();
        if (pendingMilestones.isEmpty()) {
            return new MilestoneReminderResponse(0, 0);
        }

        List<Long> recipientUserIds = groupMemberRepository
                .findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(assignment.getGroup().getId())
                .stream()
                .map(member -> member.getStudent().getUser().getId())
                .distinct()
                .toList();
        String content = reminderContent(pendingMilestones);
        for (Long recipientUserId : recipientUserIds) {
            notificationService.publish(
                    recipientUserId,
                    reminderEventKey(assignment, pendingMilestones, recipientUserId),
                    "Nhắc hạn nộp báo cáo",
                    content,
                    "/progress");
        }
        LocalDate reminderDate = LocalDate.now(clock);
        pendingMilestones.forEach(milestone -> emailNotificationEventService.manualDeadlineReminder(milestone, reminderDate));
        return new MilestoneReminderResponse(recipientUserIds.size(), pendingMilestones.size());
    }

    private String reminderContent(List<Milestone> milestones) {
        String titles = milestones.stream().map(Milestone::getTitle).collect(Collectors.joining(", "));
        return "Bạn có "
                + milestones.size()
                + " mốc báo cáo chưa nộp: "
                + titles
                + ". Vui lòng hoàn thành và nộp báo cáo trước hạn.";
    }

    private String reminderEventKey(
            ProjectAssignment assignment, List<Milestone> milestones, Long recipientUserId) {
        String milestoneIds = milestones.stream()
                .map(Milestone::getId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String fingerprint = Integer.toUnsignedString(milestoneIds.hashCode(), 36);
        return "milestone-reminder:"
                + assignment.getId()
                + ":"
                + LocalDate.now(clock)
                + ":"
                + fingerprint
                + ":"
                + recipientUserId;
    }
}
