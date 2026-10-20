package com.example.qldetai.feature.progress;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.service.AcademicAuthorization;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.*;
import com.example.qldetai.feature.project.domain.*;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.progress.domain.*;
import com.example.qldetai.feature.progress.dto.ProgressDtos.UpdateMilestoneRequest;
import com.example.qldetai.feature.progress.repository.*;
import com.example.qldetai.feature.progress.service.ProgressService;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MilestoneManagementServiceTests {
    @Mock MilestoneRepository milestones;
    @Mock ReportSubmissionRepository submissions;
    @Mock ReportFeedbackRepository feedback;
    @Mock StoredFileRepository files;
    @Mock ProjectAssignmentRepository assignments;
    @Mock StudentRepository students;
    @Mock LecturerRepository lecturers;
    @Mock UserRepository users;
    @Mock GroupMemberRepository members;
    @Mock TopicSupervisorRepository supervisors;
    @Mock NotificationService notifications;
    @Mock AcademicAuthorization academicAuthorization;
    ProgressService service;
    ProjectAssignment assignment;
    Milestone milestone;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new ProgressService(milestones, submissions, feedback, files, assignments,
                students, lecturers, users, members, supervisors, notifications, clock, academicAuthorization);
        StudentGroup group = new StudentGroup();
        group.setId(5L);
        group.setName("Nhóm A");
        Topic topic = new Topic();
        topic.setId(30L);
        Department department = new Department();
        department.setId(40L);
        topic.setDepartment(department);
        assignment = new ProjectAssignment();
        assignment.setId(7L);
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setGroup(group);
        assignment.setTopic(topic);
        milestone = milestone(10L, MilestoneStatus.OPEN, 2);
    }

    @Test
    void updatesAnUnfinishedMilestoneAndReturnsItsNewVersion() {
        asAdmin();
        when(milestones.findById(10L)).thenReturn(Optional.of(milestone));
        when(milestones.saveAndFlush(milestone)).thenAnswer(invocation -> {
            milestone.setVersion(4);
            return milestone;
        });
        var result = service.update(10L, request(3), 1L);
        assertThat(result.title()).isEqualTo("Updated report");
        assertThat(result.dueAt()).isEqualTo(LocalDateTime.now(clock).plusDays(4));
        assertThat(result.version()).isEqualTo(4);
        assertThat(result.status()).isEqualTo(MilestoneStatus.OPEN);
    }

    @Test
    void rejectsStaleEditsWithoutSaving() {
        asAdmin();
        when(milestones.findById(10L)).thenReturn(Optional.of(milestone));
        assertThatThrownBy(() -> service.update(10L, request(2), 1L))
                .isInstanceOf(BusinessRuleException.class);
        verify(milestones, never()).saveAndFlush(any());
    }

    @Test
    void cannotEditAnAcceptedMilestone() {
        asAdmin();
        milestone.setStatus(MilestoneStatus.ACCEPTED);
        when(milestones.findById(10L)).thenReturn(Optional.of(milestone));
        assertThatThrownBy(() -> service.update(10L, request(3), 1L))
                .isInstanceOf(BusinessRuleException.class);
        verify(milestones, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedLecturersCannotEditOrSendReminders() {
        User lecturerUser = new User();
        Role role = new Role();
        role.setCode(RoleCode.LECTURER);
        lecturerUser.replaceRoles(Set.of(role), LocalDateTime.now(clock));
        Lecturer lecturer = new Lecturer();
        lecturer.setId(2L);
        when(users.findWithRolesById(2L)).thenReturn(Optional.of(lecturerUser));
        when(lecturers.findByUserId(2L)).thenReturn(Optional.of(lecturer));
        when(milestones.findById(10L)).thenReturn(Optional.of(milestone));
        when(assignments.findById(7L)).thenReturn(Optional.of(assignment));
        assertThatThrownBy(() -> service.update(10L, request(3), 2L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.remind(7L, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(notifications);
        verify(milestones, never()).saveAndFlush(any());
    }

    @Test
    void remindersOnlyTargetUnsubmittedFutureMilestonesAndCurrentMembers() {
        asAdmin();
        when(assignments.findById(7L)).thenReturn(Optional.of(assignment));
        when(milestones.findByAssignmentIdOrderByDueAtAsc(7L)).thenReturn(List.of(
                milestone, milestone(11L, MilestoneStatus.SUBMITTED, 3),
                milestone(12L, MilestoneStatus.ACCEPTED, 4), milestone(13L, MilestoneStatus.OPEN, -1)));
        when(submissions.countByMilestoneId(10L)).thenReturn(0L);
        when(submissions.countByMilestoneId(11L)).thenReturn(2L);
        User recipientUser = new User();
        recipientUser.setId(20L);
        Student student = new Student();
        student.setUser(recipientUser);
        GroupMember member = new GroupMember();
        member.setStudent(student);
        when(members.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(5L)).thenReturn(List.of(member));
        var result = service.remind(7L, 1L);
        assertThat(result.recipientCount()).isEqualTo(1);
        assertThat(result.milestoneCount()).isEqualTo(1);
        verify(notifications).publish(eq(20L), eq("milestone-reminder:10:3:2026-10-20"),
                eq("Nhắc hạn nộp báo cáo"), contains("22/10/2026 10:00"), eq("/progress"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void completedAssignmentsCannotReceiveReminders() {
        asAdmin();
        assignment.setStatus(AssignmentStatus.COMPLETED);
        when(assignments.findById(7L)).thenReturn(Optional.of(assignment));
        assertThatThrownBy(() -> service.remind(7L, 1L)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(notifications);
    }

    private Milestone milestone(long id, MilestoneStatus status, int days) {
        Milestone result = new Milestone();
        result.setId(id);
        result.setAssignment(assignment);
        result.setTitle("Report " + id);
        result.setDescription("Report description");
        result.setMilestoneType(MilestoneType.PROGRESS);
        result.setStatus(status);
        result.setVersion(3);
        result.setDueAt(LocalDateTime.now(clock).plusDays(days));
        return result;
    }

    private UpdateMilestoneRequest request(int version) {
        return new UpdateMilestoneRequest(version, "Updated report", MilestoneType.PROGRESS,
                "Updated description", LocalDateTime.now(clock).plusDays(4));
    }

    private void asAdmin() {
        User admin = new User();
        Role role = new Role();
        role.setCode(RoleCode.ADMIN);
        admin.replaceRoles(Set.of(role), LocalDateTime.now(clock));
        when(users.findWithRolesById(1L)).thenReturn(Optional.of(admin));
    }
}
