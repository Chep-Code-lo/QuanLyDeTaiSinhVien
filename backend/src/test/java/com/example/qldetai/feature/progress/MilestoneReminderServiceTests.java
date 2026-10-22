package com.example.qldetai.feature.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.qldetai.feature.communication.email.EmailNotificationEventService;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.progress.domain.Milestone;
import com.example.qldetai.feature.progress.domain.MilestoneStatus;
import com.example.qldetai.feature.progress.dto.ProgressDtos.MilestoneReminderResponse;
import com.example.qldetai.feature.progress.repository.MilestoneRepository;
import com.example.qldetai.feature.progress.service.MilestoneReminderService;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

class MilestoneReminderServiceTests {

    private final ProjectAssignmentRepository assignmentRepository =
            mock(ProjectAssignmentRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final EmailNotificationEventService emailNotificationEventService =
            mock(EmailNotificationEventService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
    private final MilestoneReminderService service =
            new MilestoneReminderService(
                    assignmentRepository,
                    milestoneRepository,
                    groupMemberRepository,
                    notificationService,
                    emailNotificationEventService,
                    clock);

    @Test
    void sendsOneReminderToEachActiveMemberForUpcomingOpenMilestones() {
        ProjectAssignment assignment = assignment(11L, 17L);
        Milestone first =
                milestone(101L, assignment, "Báo cáo tiến độ lần 1", MilestoneStatus.OPEN, 1);
        Milestone second =
                milestone(102L, assignment, "Báo cáo tiến độ lần 2", MilestoneStatus.OPEN, 2);
        Milestone submitted = milestone(103L, assignment, "Đã nộp", MilestoneStatus.SUBMITTED, 3);
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(milestoneRepository.findByAssignmentIdOrderByDueAtAsc(assignment.getId()))
                .thenReturn(List.of(first, second, submitted));
        when(groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(17L))
                .thenReturn(List.of(member(301L), member(302L)));

        MilestoneReminderResponse response = service.send(assignment.getId());

        assertThat(response.recipientCount()).isEqualTo(2);
        assertThat(response.milestoneCount()).isEqualTo(2);
        verify(notificationService)
                .publish(
                        eq(301L),
                        startsWith("milestone-reminder:11:2026-10-21:"),
                        eq("Nhắc hạn nộp báo cáo"),
                        contains("Báo cáo tiến độ lần 1"),
                        eq("/progress"));
        verify(notificationService)
                .publish(
                        eq(302L),
                        startsWith("milestone-reminder:11:2026-10-21:"),
                        eq("Nhắc hạn nộp báo cáo"),
                        contains("Báo cáo tiến độ lần 2"),
                        eq("/progress"));
        verify(emailNotificationEventService)
                .manualDeadlineReminder(first, LocalDate.of(2026, 10, 21));
        verify(emailNotificationEventService)
                .manualDeadlineReminder(second, LocalDate.of(2026, 10, 21));
    }

    @Test
    void doesNotNotifyForSubmittedOrExpiredMilestones() {
        ProjectAssignment assignment = assignment(12L, 18L);
        Milestone submitted = milestone(104L, assignment, "Đã nộp", MilestoneStatus.SUBMITTED, 1);
        Milestone expired = milestone(105L, assignment, "Quá hạn", MilestoneStatus.OPEN, -1);
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(milestoneRepository.findByAssignmentIdOrderByDueAtAsc(assignment.getId()))
                .thenReturn(List.of(submitted, expired));

        MilestoneReminderResponse response = service.send(assignment.getId());

        assertThat(response.recipientCount()).isZero();
        assertThat(response.milestoneCount()).isZero();
        verifyNoInteractions(
                groupMemberRepository, notificationService, emailNotificationEventService);
    }

    private ProjectAssignment assignment(Long assignmentId, Long groupId) {
        StudentGroup group = new StudentGroup();
        group.setId(groupId);
        ProjectAssignment assignment = new ProjectAssignment();
        assignment.setId(assignmentId);
        assignment.setGroup(group);
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        return assignment;
    }

    private Milestone milestone(
            Long milestoneId,
            ProjectAssignment assignment,
            String title,
            MilestoneStatus status,
            int dueInDays) {
        Milestone milestone = new Milestone();
        milestone.setId(milestoneId);
        milestone.setAssignment(assignment);
        milestone.setTitle(title);
        milestone.setStatus(status);
        milestone.setDueAt(LocalDateTime.now(clock).plusDays(dueInDays));
        return milestone;
    }

    private GroupMember member(Long userId) {
        User user = new User();
        user.setId(userId);
        Student student = new Student();
        student.setUser(user);
        GroupMember member = new GroupMember();
        member.setStudent(student);
        return member;
    }
}
