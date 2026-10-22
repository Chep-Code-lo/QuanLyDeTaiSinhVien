package com.example.qldetai.feature.groupregistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.communication.repository.NotificationRepository;
import com.example.qldetai.feature.groupregistration.domain.GroupInvitation;
import com.example.qldetai.feature.groupregistration.domain.GroupInvitationStatus;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupInvitationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.GroupInvitationResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.StudentGroupResponse;
import com.example.qldetai.feature.groupregistration.repository.GroupInvitationRepository;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.service.GroupInvitationService;
import com.example.qldetai.feature.groupregistration.service.GroupService;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

@SpringBootTest
@Import(GroupInvitationServiceIntegrationTests.InvitationClockConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class GroupInvitationServiceIntegrationTests {

    private static final Instant START = Instant.parse("2030-01-15T09:00:00Z");

    private final GroupInvitationService invitationService;
    private final GroupService groupService;
    private final GroupInvitationRepository invitationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final NotificationRepository notificationRepository;
    private final TestClock clock;

    private Faculty faculty;
    private Department department;
    private RegistrationPeriod period;
    private Student leader;
    private Student invitee;

    GroupInvitationServiceIntegrationTests(
            GroupInvitationService invitationService,
            GroupService groupService,
            GroupInvitationRepository invitationRepository,
            GroupMemberRepository groupMemberRepository,
            StudentGroupRepository studentGroupRepository,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            SemesterRepository semesterRepository,
            RegistrationPeriodRepository periodRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            NotificationRepository notificationRepository,
            TestClock clock) {
        this.invitationService = invitationService;
        this.groupService = groupService;
        this.invitationRepository = invitationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
        this.periodRepository = periodRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        clock.set(START);
        String token = Long.toUnsignedString(System.nanoTime(), 36);

        faculty = new Faculty();
        faculty.setCode("F" + token);
        faculty.setName("Faculty " + token);
        faculty.setActive(true);
        faculty = facultyRepository.save(faculty);

        department = new Department();
        department.setFaculty(faculty);
        department.setCode("D" + token);
        department.setName("Department " + token);
        department.setActive(true);
        department = departmentRepository.save(department);

        leader = createStudent("leader-" + token, "Nhóm trưởng");
        invitee = createStudent("invitee-" + token, "Sinh viên được mời");

        Semester semester = new Semester();
        semester.setAcademicYear("2030-" + token);
        semester.setSemesterNo(1);
        semester.setStartsOn(LocalDate.of(2030, 1, 1));
        semester.setEndsOn(LocalDate.of(2030, 6, 30));
        semester = semesterRepository.save(semester);

        LocalDateTime now = now();
        period = new RegistrationPeriod();
        period.setFaculty(faculty);
        period.setSemester(semester);
        period.setName("Invitation period " + token);
        period.setProjectType(ProjectType.COURSE);
        period.setProposalStartAt(now.minusDays(1));
        period.setProposalEndAt(now.plusDays(1));
        period.setRegistrationStartAt(now.minusDays(1));
        period.setRegistrationEndAt(now.plusDays(1));
        period.setReviewDeadlineAt(now.plusDays(7));
        period.setDefenseDate(now.toLocalDate().plusDays(30));
        period.setMinGroupMembers(2);
        period.setMaxGroupMembers(3);
        period.setInvitationExpiryHours(2);
        period.setMinCommitteeMembers(3);
        period.setMaxCommitteeMembers(5);
        period.setStatus(RegistrationPeriodStatus.ACTIVE);
        period.setCreatedBy(leader.getUser());
        period.setCreatedAt(now);
        period = periodRepository.save(period);
    }

    @Test
    void acceptingInvitationCreatesMembershipAndActivatesGroupInTheSameWorkflow() {
        StudentGroupResponse group = createGroup();
        GroupInvitationResponse invitation = createInvitation(group, now().plusMinutes(30));

        GroupInvitationResponse accepted =
                invitationService.accept(invitation.id(), invitee.getUser().getId());

        assertThat(accepted.status()).isEqualTo(GroupInvitationStatus.ACCEPTED);
        assertThat(accepted.respondedAt()).isNotNull();
        assertThat(
                        groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(
                                group.id(), invitee.getId()))
                .hasValueSatisfying(
                        member ->
                                assertThat(member.getMemberRole())
                                        .isEqualTo(GroupMemberRole.MEMBER));
        assertThat(groupService.get(group.id()).status()).isEqualTo(GroupStatus.ACTIVE);
        assertThat(invitationRepository.findById(invitation.id()))
                .hasValueSatisfying(
                        saved ->
                                assertThat(saved.getStatus())
                                        .isEqualTo(GroupInvitationStatus.ACCEPTED));
    }

    @Test
    void cancellingInvitationDoesNotNotifyTheInvitedStudent() {
        StudentGroupResponse group = createGroup();
        GroupInvitationResponse invitation = createInvitation(group, now().plusMinutes(30));
        Long recipientId = invitee.getUser().getId();
        long unreadBefore = notificationRepository.countByUserIdAndReadAtIsNull(recipientId);

        GroupInvitationResponse cancelled =
                invitationService.cancel(invitation.id(), leader.getUser().getId());

        assertThat(cancelled.status()).isEqualTo(GroupInvitationStatus.CANCELLED);
        assertThat(cancelled.respondedAt()).isNotNull();
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(recipientId))
                .isEqualTo(unreadBefore);
        assertThat(
                        notificationRepository.existsByUserIdAndEventKey(
                                recipientId, "group-invitation-cancelled-" + invitation.id()))
                .isFalse();
        assertThat(
                        notificationRepository.existsByUserIdAndEventKey(
                                recipientId, "group-invitation-" + invitation.id()))
                .isTrue();
    }

    @Test
    void rejectingInvitationLeavesTheInviteeOutsideTheGroup() {
        StudentGroupResponse group = createGroup();
        GroupInvitationResponse invitation = createInvitation(group, now().plusMinutes(30));

        GroupInvitationResponse rejected =
                invitationService.reject(invitation.id(), invitee.getUser().getId());

        assertThat(rejected.status()).isEqualTo(GroupInvitationStatus.REJECTED);
        assertThat(rejected.respondedAt()).isNotNull();
        assertThat(
                        groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(
                                group.id(), invitee.getId()))
                .isEmpty();
        assertThat(invitationRepository.findById(invitation.id()))
                .hasValueSatisfying(
                        saved ->
                                assertThat(saved.getStatus())
                                        .isEqualTo(GroupInvitationStatus.REJECTED));
    }

    @Test
    void invitationThatExpiresBetweenSweepAndLockCannotBeAccepted() {
        StudentGroupResponse group = createGroup();
        StudentGroup groupEntity = studentGroupRepository.findById(group.id()).orElseThrow();
        LocalDateTime sweepTime = now();

        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroup(groupEntity);
        invitation.setInvitedStudent(invitee);
        invitation.setInvitedBy(leader);
        invitation.setStatus(GroupInvitationStatus.PENDING);
        invitation.setCreatedAt(sweepTime.minusMinutes(1));
        invitation.setExpiresAt(sweepTime.plusSeconds(1));
        invitation = invitationRepository.saveAndFlush(invitation);

        // The first instant is used by the bulk sweep; the second is reached after the row lock.
        LocalDateTime expiredAtLock = sweepTime.plusSeconds(2);
        clock.schedule(
                sweepTime.toInstant(ZoneOffset.UTC), expiredAtLock.toInstant(ZoneOffset.UTC));

        GroupInvitationResponse response =
                invitationService.accept(invitation.getId(), invitee.getUser().getId());

        assertThat(response.status()).isEqualTo(GroupInvitationStatus.EXPIRED);
        assertThat(response.respondedAt()).isEqualTo(expiredAtLock);
        assertThat(
                        groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(
                                group.id(), invitee.getId()))
                .isEmpty();
        assertThat(invitationRepository.findById(invitation.getId()))
                .hasValueSatisfying(
                        saved ->
                                assertThat(saved.getStatus())
                                        .isEqualTo(GroupInvitationStatus.EXPIRED));
    }

    @Test
    void createRejectsAnExpiryBeyondThePeriodConfiguredLifetime() {
        StudentGroupResponse group = createGroup();

        assertThatThrownBy(
                        () ->
                                invitationService.create(
                                        new CreateGroupInvitationRequest(
                                                group.id(),
                                                invitee.getStudentCode(),
                                                now().plusHours(3)),
                                        leader.getUser().getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("configured invitation lifetime");
    }

    @Test
    void acceptingInvitationAllowsMembershipInAnotherRegistrationPeriod() {
        StudentGroupResponse group = createGroup();
        GroupInvitationResponse invitation = createInvitation(group, now().plusMinutes(30));
        RegistrationPeriod other = new RegistrationPeriod();
        other.setFaculty(faculty);
        other.setSemester(period.getSemester());
        other.setName("Other concurrent period");
        other.setProjectType(ProjectType.COURSE);
        other.setProposalStartAt(period.getProposalStartAt());
        other.setProposalEndAt(period.getProposalEndAt());
        other.setRegistrationStartAt(period.getRegistrationStartAt());
        other.setRegistrationEndAt(period.getRegistrationEndAt());
        other.setStatus(RegistrationPeriodStatus.ACTIVE);
        other.setCreatedBy(leader.getUser());
        other.setCreatedAt(now());
        other = periodRepository.saveAndFlush(other);
        StudentGroupResponse concurrent =
                groupService.create(
                        new CreateGroupRequest(other.getId(), "Concurrent group"),
                        invitee.getUser().getId());
        assertThat(invitationService.accept(invitation.id(), invitee.getUser().getId()).status())
                .isEqualTo(GroupInvitationStatus.ACCEPTED);
        assertThat(
                        groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(
                                group.id(), invitee.getId()))
                .isPresent();
        assertThat(
                        groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(
                                concurrent.id(), invitee.getId()))
                .isPresent();
    }

    private StudentGroupResponse createGroup() {
        return groupService.create(
                new CreateGroupRequest(period.getId(), "Nhóm mời " + System.nanoTime()),
                leader.getUser().getId());
    }

    private GroupInvitationResponse createInvitation(
            StudentGroupResponse group, LocalDateTime expiresAt) {
        return invitationService.create(
                new CreateGroupInvitationRequest(group.id(), invitee.getStudentCode(), expiresAt),
                leader.getUser().getId());
    }

    private Student createStudent(String code, String fullName) {
        LocalDateTime now = now();
        Role studentRole = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();

        User user = new User();
        user.setEmail(code + "@test.local");
        user.setPasswordHash("not-used-by-service-tests");
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(studentRole), now);
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setDepartment(department);
        student.setStudentCode(code.toUpperCase());
        student.setClassName("D21CQCN01");
        student.setCohort("K21");
        return studentRepository.save(student);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.currentInstant(), ZoneOffset.UTC);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InvitationClockConfiguration {

        @Bean
        @Primary
        TestClock invitationTestClock() {
            return new TestClock(START);
        }
    }

    static final class TestClock extends Clock {

        private final Deque<Instant> scheduledInstants = new ArrayDeque<>();
        private Instant current;

        TestClock(Instant initial) {
            this.current = initial;
        }

        synchronized void set(Instant instant) {
            scheduledInstants.clear();
            current = instant;
        }

        synchronized void schedule(Instant... instants) {
            scheduledInstants.clear();
            scheduledInstants.addAll(List.of(instants));
            current = instants[instants.length - 1];
        }

        synchronized Instant currentInstant() {
            return current;
        }

        @Override
        public synchronized Instant instant() {
            Instant next = scheduledInstants.pollFirst();
            return next == null ? current : next;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return zone.equals(getZone()) ? this : Clock.fixed(instant(), zone);
        }
    }
}
