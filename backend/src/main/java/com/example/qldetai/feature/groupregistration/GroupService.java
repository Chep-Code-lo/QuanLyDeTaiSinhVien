package com.example.qldetai.feature.groupregistration;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.RegistrationPeriod;
import com.example.qldetai.feature.academic.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.RegistrationPeriodStatus;
import com.example.qldetai.feature.communication.NotificationService;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.AddGroupMemberRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.GroupMemberResponse;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.StudentGroupResponse;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.Student;
import com.example.qldetai.feature.identity.StudentRepository;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.project.ProjectAssignmentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private final StudentGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final TopicRegistrationRepository registrationRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public GroupService(
            StudentGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            TopicRegistrationRepository registrationRepository,
            ProjectAssignmentRepository assignmentRepository,
            RegistrationPeriodRepository periodRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this(
                groupRepository,
                memberRepository,
                registrationRepository,
                assignmentRepository,
                periodRepository,
                studentRepository,
                userRepository,
                notificationService,
                Clock.systemUTC());
    }

    GroupService(
            StudentGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            TopicRegistrationRepository registrationRepository,
            ProjectAssignmentRepository assignmentRepository,
            RegistrationPeriodRepository periodRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.registrationRepository = registrationRepository;
        this.assignmentRepository = assignmentRepository;
        this.periodRepository = periodRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentGroupResponse> list(Long periodId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StudentGroup> groups = periodId == null
                ? groupRepository.findAll(pageable)
                : groupRepository.findByPeriodId(periodId, pageable);
        return PageResponse.from(groups.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<StudentGroupResponse> mine(Long actorId) {
        Student student = requireStudent(actorId);
        Map<Long, StudentGroupResponse> groups = new LinkedHashMap<>();
        memberRepository.findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(student.getId())
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
        if (memberRepository.existsByPeriodIdAndStudentIdAndLeftAtIsNull(period.getId(), creator.getId())) {
            throw new BusinessRuleException("Student already belongs to an active group in this registration period.");
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
        group.setCreatedAt(now);
        group = groupRepository.save(group);

        GroupMember leader = new GroupMember();
        leader.setGroup(group);
        leader.setPeriod(period);
        leader.setStudent(creator);
        leader.setSlotNo(1);
        leader.setMemberRole(GroupMemberRole.LEADER);
        leader.setJoinedAt(now);
        memberRepository.save(leader);

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
        if (memberRepository.existsByPeriodIdAndStudentIdAndLeftAtIsNull(period.getId(), member.getId())) {
            throw new BusinessRuleException("Student already belongs to an active group in this registration period.");
        }
        List<GroupMember> activeMembers = memberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(groupId);
        if (activeMembers.size() >= 3) {
            throw new BusinessRuleException("A student group can contain at most three active members.");
        }

        GroupMember groupMember = new GroupMember();
        groupMember.setGroup(group);
        groupMember.setPeriod(period);
        groupMember.setStudent(member);
        groupMember.setSlotNo(nextAvailableSlot(activeMembers));
        groupMember.setMemberRole(GroupMemberRole.MEMBER);
        groupMember.setJoinedAt(LocalDateTime.now(clock));
        memberRepository.save(groupMember);
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

        GroupMember member = memberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(groupId, studentId)
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
        if (assignmentRepository.existsByGroupId(group.getId())) {
            throw new BusinessRuleException("This group already has a project assignment and can no longer change members.");
        }
        if (registrationRepository.existsByGroupIdAndStatusIn(
                group.getId(), List.of(RegistrationStatus.PENDING, RegistrationStatus.APPROVED))) {
            throw new BusinessRuleException("Group members cannot change after a topic registration is pending or approved.");
        }
    }

    private RegistrationPeriod requireRegistrationWindow(Long periodId) {
        RegistrationPeriod period = periodRepository.findById(periodId)
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
        GroupMember membership = memberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(groupId, studentId)
                .orElseThrow(() -> new AccessDeniedException("Only active group members can manage this group."));
        if (membership.getMemberRole() != GroupMemberRole.LEADER) {
            throw new AccessDeniedException("Only the group leader can manage group members.");
        }
    }

    private StudentGroup requireGroup(Long id) {
        return groupRepository.findById(id)
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
        long activeMembers = memberRepository.countByGroupIdAndLeftAtIsNull(group.getId());
        group.setStatus(activeMembers >= 2 ? GroupStatus.ACTIVE : GroupStatus.FORMING);
    }

    private int nextAvailableSlot(List<GroupMember> activeMembers) {
        for (int slot = 1; slot <= 3; slot++) {
            int candidate = slot;
            boolean used = activeMembers.stream().anyMatch(member -> member.getSlotNo() == candidate);
            if (!used) {
                return candidate;
            }
        }
        throw new BusinessRuleException("A student group can contain at most three active members.");
    }

    private StudentGroupResponse toResponse(StudentGroup group) {
        List<GroupMemberResponse> members = memberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(group.getId())
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
