package com.example.qldetai.feature.groupregistration.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.GroupInvitation;
import com.example.qldetai.feature.groupregistration.domain.GroupInvitationStatus;
import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupInvitationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.GroupInvitationResponse;
import com.example.qldetai.feature.groupregistration.repository.GroupInvitationRepository;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitation-only membership workflow.  Acceptance and creation of the membership row happen in
 * the same transaction, so an invitation can never bypass the period/group constraints.
 */
@Service
public class GroupInvitationService {

    private final GroupInvitationRepository groupInvitationRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public GroupInvitationService(
            GroupInvitationRepository groupInvitationRepository,
            StudentGroupRepository studentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            TopicRegistrationRepository topicRegistrationRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.groupInvitationRepository = groupInvitationRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.topicRegistrationRepository = topicRegistrationRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public PageResponse<GroupInvitationResponse> mine(Long actorId, int page, int size) {
        expireStale();
        Student student = requireStudent(actorId);
        return PageResponse.from(groupInvitationRepository.findByInvitedStudentIdOrderByCreatedAtDesc(
                        student.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse));
    }

    @Transactional
    public List<GroupInvitationResponse> listForGroup(Long groupId, Long actorId) {
        expireStale();
        Student actor = requireStudent(actorId);
        requireLeader(groupId, actor.getId());
        return groupInvitationRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @BusinessAudit(action = "GROUP_INVITATION_CREATE", entityType = "GROUP_INVITATION")
    public GroupInvitationResponse create(CreateGroupInvitationRequest request, Long actorId) {
        expireStale();
        Student actor = requireStudent(actorId);
        StudentGroup group = requireGroupForUpdate(request.groupId());
        requireLeader(group.getId(), actor.getId());
        RegistrationPeriod period = requireOpenRegistrationWindow(group);
        ensureGroupMutable(group);

        Student invitedStudent = studentRepository.findByStudentCodeIgnoreCase(request.studentCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found."));
        ensureEligibleInvitee(group, invitedStudent);

        LocalDateTime now = LocalDateTime.now(clock);
        if (!request.expiresAt().isAfter(now)) {
            throw new BusinessRuleException("Invitation expiry must be in the future.");
        }
        LocalDateTime policyExpiry = now.plusHours(period.getInvitationExpiryHours());
        LocalDateTime latestExpiry = policyExpiry.isBefore(period.getRegistrationEndAt())
                ? policyExpiry
                : period.getRegistrationEndAt();
        if (request.expiresAt().isAfter(latestExpiry)) {
            throw new BusinessRuleException(
                    "Invitation expiry must be within the configured invitation lifetime and registration window.");
        }
        if (groupInvitationRepository.existsByGroupIdAndInvitedStudentIdAndStatus(
                group.getId(), invitedStudent.getId(), GroupInvitationStatus.PENDING)) {
            throw new BusinessRuleException("This student already has a pending invitation to the group.");
        }

        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroup(group);
        invitation.setInvitedStudent(invitedStudent);
        invitation.setInvitedBy(actor);
        invitation.setStatus(GroupInvitationStatus.PENDING);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(request.expiresAt());
        invitation = groupInvitationRepository.save(invitation);

        notificationService.publish(
                invitedStudent.getUser().getId(),
                "group-invitation-" + invitation.getId(),
                "Lời mời vào nhóm",
                actor.getUser().getFullName() + " mời bạn tham gia nhóm " + group.getName() + ".",
                "/groups");
        return toResponse(invitation);
    }

    @Transactional
    @BusinessAudit(action = "GROUP_INVITATION_ACCEPT", entityType = "GROUP_INVITATION", entityIdArgument = 0)
    public GroupInvitationResponse accept(Long invitationId, Long actorId) {
        expireStale();
        Student actor = requireStudent(actorId);
        GroupInvitation invitation = requireInvitationForUpdate(invitationId);
        if (!invitation.getInvitedStudent().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the invited student can accept this invitation.");
        }
        if (expireIfDue(invitation)) {
            return toResponse(invitation);
        }
        requirePending(invitation);

        StudentGroup group = requireGroupForUpdate(invitation.getGroup().getId());
        RegistrationPeriod period = requireOpenRegistrationWindow(group);
        ensureGroupMutable(group);
        ensureEligibleInvitee(group, actor);

        List<GroupMember> activeMembers = groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(group.getId());
        if (activeMembers.size() >= period.getMaxGroupMembers()) {
            throw new BusinessRuleException("The group has reached its configured maximum number of members.");
        }

        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setPeriod(period);
        member.setStudent(actor);
        member.setSlotNo(nextAvailableSlot(activeMembers, period.getMaxGroupMembers()));
        member.setMemberRole(GroupMemberRole.MEMBER);
        member.setJoinedAt(LocalDateTime.now(clock));
        groupMemberRepository.save(member);

        invitation.setStatus(GroupInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now(clock));
        refreshGroupStatus(group);
        notificationService.publish(
                invitation.getInvitedBy().getUser().getId(),
                "group-invitation-accepted-" + invitation.getId(),
                "Lời mời đã được chấp nhận",
                actor.getUser().getFullName() + " đã tham gia nhóm " + group.getName() + ".",
                "/groups");
        return toResponse(invitation);
    }

    @Transactional
    @BusinessAudit(action = "GROUP_INVITATION_REJECT", entityType = "GROUP_INVITATION", entityIdArgument = 0)
    public GroupInvitationResponse reject(Long invitationId, Long actorId) {
        expireStale();
        Student actor = requireStudent(actorId);
        GroupInvitation invitation = requireInvitationForUpdate(invitationId);
        if (!invitation.getInvitedStudent().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the invited student can reject this invitation.");
        }
        if (expireIfDue(invitation)) {
            return toResponse(invitation);
        }
        requirePending(invitation);
        invitation.setStatus(GroupInvitationStatus.REJECTED);
        invitation.setRespondedAt(LocalDateTime.now(clock));
        notificationService.publish(
                invitation.getInvitedBy().getUser().getId(),
                "group-invitation-rejected-" + invitation.getId(),
                "Lời mời đã bị từ chối",
                actor.getUser().getFullName() + " đã từ chối lời mời vào nhóm " + invitation.getGroup().getName() + ".",
                "/groups");
        return toResponse(invitation);
    }

    @Transactional
    @BusinessAudit(action = "GROUP_INVITATION_CANCEL", entityType = "GROUP_INVITATION", entityIdArgument = 0)
    public GroupInvitationResponse cancel(Long invitationId, Long actorId) {
        expireStale();
        Student actor = requireStudent(actorId);
        GroupInvitation invitation = requireInvitationForUpdate(invitationId);
        StudentGroup group = requireGroupForUpdate(invitation.getGroup().getId());
        requireLeader(group.getId(), actor.getId());
        if (expireIfDue(invitation)) {
            return toResponse(invitation);
        }
        requirePending(invitation);
        invitation.setStatus(GroupInvitationStatus.CANCELLED);
        invitation.setRespondedAt(LocalDateTime.now(clock));
        notificationService.publish(
                invitation.getInvitedStudent().getUser().getId(),
                "group-invitation-cancelled-" + invitation.getId(),
                "Lời mời vào nhóm đã bị hủy",
                "Trưởng nhóm đã hủy lời mời tham gia nhóm " + group.getName() + ".",
                "/groups");
        return toResponse(invitation);
    }

    private void expireStale() {
        groupInvitationRepository.expirePendingAtOrBefore(LocalDateTime.now(clock));
    }

    /**
     * The bulk expiry sweep happens before an invitation row is locked. Re-check its timestamp
     * after the lock as well, because the invitation may expire in that small interval.
     */
    private boolean expireIfDue(GroupInvitation invitation) {
        if (invitation.getStatus() == GroupInvitationStatus.EXPIRED) {
            return true;
        }
        if (invitation.getStatus() != GroupInvitationStatus.PENDING) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (invitation.getExpiresAt().isAfter(now)) {
            return false;
        }
        invitation.setStatus(GroupInvitationStatus.EXPIRED);
        invitation.setRespondedAt(now);
        return true;
    }

    private void requirePending(GroupInvitation invitation) {
        if (invitation.getStatus() != GroupInvitationStatus.PENDING) {
            throw new BusinessRuleException("This invitation is no longer pending.");
        }
    }

    private StudentGroup requireGroupForUpdate(Long groupId) {
        return studentGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Student group was not found."));
    }

    private GroupInvitation requireInvitationForUpdate(Long invitationId) {
        return groupInvitationRepository.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Group invitation was not found."));
    }

    private RegistrationPeriod requireOpenRegistrationWindow(StudentGroup group) {
        RegistrationPeriod period = group.getPeriod();
        if (period.getStatus() != RegistrationPeriodStatus.ACTIVE) {
            throw new BusinessRuleException("Group invitations are only available in an active registration period.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(period.getRegistrationStartAt()) || now.isAfter(period.getRegistrationEndAt())) {
            throw new BusinessRuleException("Group invitations are only available during the registration window.");
        }
        return period;
    }

    private void ensureGroupMutable(StudentGroup group) {
        if (group.getStatus() == GroupStatus.LOCKED || group.getStatus() == GroupStatus.CLOSED) {
            throw new BusinessRuleException("This group is locked and can no longer change members.");
        }
        if (projectAssignmentRepository.existsByGroupId(group.getId())) {
            throw new BusinessRuleException("This group already has a project assignment and can no longer change members.");
        }
        if (topicRegistrationRepository.existsByGroupIdAndStatusIn(
                group.getId(), List.of(RegistrationStatus.PENDING, RegistrationStatus.APPROVED))) {
            throw new BusinessRuleException("Group members cannot change after a topic registration is pending or approved.");
        }
    }

    private void ensureEligibleInvitee(StudentGroup group, Student student) {
        RegistrationPeriod period = group.getPeriod();
        if (!student.getDepartment().getFaculty().getId().equals(period.getFaculty().getId())) {
            throw new BusinessRuleException("All group members must belong to the same faculty as the registration period.");
        }
        if (groupMemberRepository.existsByPeriodIdAndStudentIdAndLeftAtIsNull(period.getId(), student.getId())) {
            throw new BusinessRuleException("Student already belongs to an active group in this registration period.");
        }
        if (groupMemberRepository.countByGroupIdAndLeftAtIsNull(group.getId()) >= period.getMaxGroupMembers()) {
            throw new BusinessRuleException("The group has reached its configured maximum number of members.");
        }
    }

    private void requireLeader(Long groupId, Long studentId) {
        GroupMember membership = groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(groupId, studentId)
                .orElseThrow(() -> new AccessDeniedException("Only active group members can manage this group."));
        if (membership.getMemberRole() != GroupMemberRole.LEADER) {
            throw new AccessDeniedException("Only the group leader can manage group invitations.");
        }
    }

    private Student requireStudent(Long actorId) {
        User user = userRepository.findWithRolesById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        if (user.getUserRoles().stream().noneMatch(userRole -> userRole.getRole().getCode() == RoleCode.STUDENT)) {
            throw new AccessDeniedException("A student account is required for this action.");
        }
        return studentRepository.findByUserId(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile was not found."));
    }

    private void refreshGroupStatus(StudentGroup group) {
        long activeMembers = groupMemberRepository.countByGroupIdAndLeftAtIsNull(group.getId());
        group.setStatus(activeMembers >= group.getPeriod().getMinGroupMembers() ? GroupStatus.ACTIVE : GroupStatus.FORMING);
    }

    private int nextAvailableSlot(List<GroupMember> activeMembers, int maxGroupMembers) {
        for (int slot = 1; slot <= maxGroupMembers; slot++) {
            int candidate = slot;
            boolean used = activeMembers.stream().anyMatch(member -> member.getSlotNo() == candidate);
            if (!used) {
                return candidate;
            }
        }
        throw new BusinessRuleException("The group has reached its configured maximum number of members.");
    }

    private GroupInvitationResponse toResponse(GroupInvitation invitation) {
        return new GroupInvitationResponse(
                invitation.getId(),
                invitation.getGroup().getId(),
                invitation.getGroup().getName(),
                invitation.getGroup().getPeriod().getId(),
                invitation.getGroup().getPeriod().getName(),
                invitation.getInvitedStudent().getId(),
                invitation.getInvitedStudent().getStudentCode(),
                invitation.getInvitedStudent().getUser().getFullName(),
                invitation.getInvitedBy().getId(),
                invitation.getInvitedBy().getUser().getFullName(),
                invitation.getStatus(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getRespondedAt());
    }
}
