package com.example.qldetai.feature.groupregistration.service;

import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.AddGroupMemberRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.GroupMemberResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.StudentGroupResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final StudentGroupRepository studentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;


    @Transactional(readOnly = true)
    public PageResponse<StudentGroupResponse> list(Long periodId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StudentGroup> groups = periodId == null
                ? studentGroupRepository.findAll(pageable)
                : studentGroupRepository.findByPeriodId(periodId, pageable);
        return PageResponse.from(groups.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<StudentGroupResponse> mine(Long actorId) {
        Student student = requireStudent(actorId);
        Map<Long, StudentGroupResponse> groups = new LinkedHashMap<>();
        groupMemberRepository.findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(student.getId())
                .forEach(member -> groups.putIfAbsent(member.getGroup().getId(), toResponse(member.getGroup())));
        return List.copyOf(groups.values());
    }

    @Transactional(readOnly = true)
    public StudentGroupResponse get(Long id) {
        return toResponse(requireGroup(id));
    }

    @Transactional
    @BusinessAudit(action = "GROUP_CREATE", entityType = "STUDENT_GROUP")
    public StudentGroupResponse create(CreateGroupRequest request, Long actorId) {
        Student creator = requireStudent(actorId);
        RegistrationPeriod period = requireRegistrationWindow(request.periodId());
        studentRepository.findByIdForUpdate(creator.getId()).orElseThrow();
        if (!groupMemberRepository.findConflictingMemberships(period.getId(), creator.getId()).isEmpty()) {
            throw new BusinessRuleException("Student already belongs to an active group.");
        }
        if (!creator.getDepartment().getFaculty().getId().equals(period.getFaculty().getId())) {
            throw new BusinessRuleException("Student must belong to the same faculty as the registration period.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        StudentGroup group = new StudentGroup();
        group.setPeriod(period);
        group.setName(request.name().trim());
        group.setStatus(GroupStatus.FORMING);
        group.setCreatedBy(creator);
        group = studentGroupRepository.save(group);

        GroupMember leader = new GroupMember();
        leader.setGroup(group);
        leader.setPeriod(period);
        leader.setStudent(creator);
        leader.setSlotNo(1);
        leader.setMemberRole(GroupMemberRole.LEADER);
        leader.setJoinedAt(now);
        groupMemberRepository.save(leader);
        refreshGroupStatus(group);

        return toResponse(group);
    }

    @Transactional
    @BusinessAudit(action = "GROUP_MEMBER_ADD", entityType = "STUDENT_GROUP", entityIdArgument = 0)
    public StudentGroupResponse addMember(Long groupId, AddGroupMemberRequest request, Long actorId) {
        Student actor = requireStudent(actorId);
        StudentGroup group = requireGroup(groupId);
        RegistrationPeriod period = group.getPeriod();
        requireLeader(groupId, actor.getId());
        ensureGroupMutable(group);

        Student member = studentRepository.findByStudentCodeIgnoreCase(request.studentCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found."));
        if (!member.getDepartment().getFaculty().getId().equals(period.getFaculty().getId())) {
            throw new BusinessRuleException("All group members must belong to the same faculty as the registration period.");
        }
        studentRepository.findByIdForUpdate(member.getId()).orElseThrow();
        if (!groupMemberRepository.findConflictingMemberships(period.getId(), member.getId()).isEmpty()) {
            throw new BusinessRuleException("Student already belongs to an active group.");
        }
        List<GroupMember> activeMembers =
                groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(groupId);
        if (activeMembers.size() >= period.getMaxGroupMembers()) {
            throw new BusinessRuleException("The group has reached its configured maximum number of members.");
        }

        GroupMember groupMember = new GroupMember();
        groupMember.setGroup(group);
        groupMember.setPeriod(period);
        groupMember.setStudent(member);
        groupMember.setSlotNo(nextAvailableSlot(activeMembers, period.getMaxGroupMembers()));
        groupMember.setMemberRole(GroupMemberRole.MEMBER);
        groupMember.setJoinedAt(LocalDateTime.now(clock));
        groupMemberRepository.save(groupMember);
        refreshGroupStatus(group);

        notificationService.publish(
                member.getUser().getId(),
                "group-member-added-" + groupId + "-" + member.getId(),
                "Bạn đã được thêm vào nhóm",
                "Nhóm " + group.getName() + " vừa thêm bạn làm thành viên.",
                "/groups/" + groupId);

        return toResponse(group);
    }

    @Transactional
    @BusinessAudit(action = "GROUP_MEMBER_REMOVE", entityType = "STUDENT_GROUP", entityIdArgument = 0)
    public StudentGroupResponse removeMember(Long groupId, Long studentId, Long actorId) {
        Student actor = requireStudent(actorId);
        StudentGroup group = requireGroup(groupId);
        requireLeader(groupId, actor.getId());
        ensureGroupMutable(group);

        GroupMember member = groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(groupId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Active group member was not found."));
        if (member.getMemberRole() == GroupMemberRole.LEADER) {
            throw new BusinessRuleException("The group leader cannot be removed from the group.");
        }

        member.setLeftAt(LocalDateTime.now(clock));
        refreshGroupStatus(group);
        return toResponse(group);
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

    private RegistrationPeriod requireRegistrationWindow(Long periodId) {
        RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration period was not found."));
        if (period.getStatus() != RegistrationPeriodStatus.ACTIVE) {
            throw new BusinessRuleException("Student groups can only be created in an active registration period.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(period.getRegistrationStartAt()) || now.isAfter(period.getRegistrationEndAt())) {
            throw new BusinessRuleException("Student groups can only be managed during the registration window.");
        }
        return period;
    }

    private void requireLeader(Long groupId, Long studentId) {
        GroupMember membership = groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(groupId, studentId)
                .orElseThrow(() -> new AccessDeniedException("Only active group members can manage this group."));
        if (membership.getMemberRole() != GroupMemberRole.LEADER) {
            throw new AccessDeniedException("Only the group leader can manage group members.");
        }
    }

    private StudentGroup requireGroup(Long id) {
        return studentGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student group was not found."));
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
        group.setStatus(activeMembers >= group.getPeriod().getMinGroupMembers()
                ? GroupStatus.ACTIVE
                : GroupStatus.FORMING);
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

    private StudentGroupResponse toResponse(StudentGroup group) {
        List<GroupMemberResponse> members =
                groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(group.getId())
                .stream()
                .map(member -> new GroupMemberResponse(
                        member.getStudent().getId(),
                        member.getStudent().getUser().getId(),
                        member.getStudent().getStudentCode(),
                        member.getStudent().getUser().getFullName(),
                        member.getStudent().getClassName(),
                        member.getStudent().getCohort(),
                        member.getSlotNo(),
                        member.getMemberRole(),
                        member.getJoinedAt()))
                .toList();

        return new StudentGroupResponse(
                group.getId(),
                group.getPeriod().getId(),
                group.getPeriod().getName(),
                group.getName(),
                group.getStatus(),
                group.getCreatedBy().getId(),
                group.getCreatedBy().getUser().getFullName(),
                group.getCreatedAt(),
                group.getVersion(),
                members);
    }
}
