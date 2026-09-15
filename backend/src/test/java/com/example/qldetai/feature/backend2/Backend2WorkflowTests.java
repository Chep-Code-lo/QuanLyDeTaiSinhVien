package com.example.qldetai.feature.backend2;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.feature.academic.Department;
import com.example.qldetai.feature.academic.DepartmentRepository;
import com.example.qldetai.feature.academic.Faculty;
import com.example.qldetai.feature.academic.FacultyRepository;
import com.example.qldetai.feature.academic.ProjectType;
import com.example.qldetai.feature.academic.RegistrationPeriod;
import com.example.qldetai.feature.academic.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.Semester;
import com.example.qldetai.feature.academic.SemesterRepository;
import com.example.qldetai.feature.groupregistration.GroupMember;
import com.example.qldetai.feature.groupregistration.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.AddGroupMemberRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.CreateTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.ReviewTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.StudentGroupResponse;
import com.example.qldetai.feature.groupregistration.GroupRegistrationDtos.TopicRegistrationResponse;
import com.example.qldetai.feature.groupregistration.GroupService;
import com.example.qldetai.feature.groupregistration.GroupStatus;
import com.example.qldetai.feature.groupregistration.RegistrationService;
import com.example.qldetai.feature.groupregistration.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.TopicRegistration;
import com.example.qldetai.feature.groupregistration.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.Lecturer;
import com.example.qldetai.feature.identity.LecturerRepository;
import com.example.qldetai.feature.identity.Role;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.RoleRepository;
import com.example.qldetai.feature.identity.Student;
import com.example.qldetai.feature.identity.StudentRepository;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.identity.UserStatus;
import com.example.qldetai.feature.project.AssignmentStatus;
import com.example.qldetai.feature.project.ProjectAssignmentRepository;
import com.example.qldetai.feature.project.ProjectAssignmentService;
import com.example.qldetai.feature.project.ProjectDtos.CreateAssignmentRequest;
import com.example.qldetai.feature.project.ProjectDtos.ProjectAssignmentResponse;
import com.example.qldetai.feature.project.ProjectDtos.UpdateAssignmentStatusRequest;
import com.example.qldetai.feature.topic.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.TopicDtos.PublishTopicRequest;
import com.example.qldetai.feature.topic.TopicDtos.ReviewTopicRequest;
import com.example.qldetai.feature.topic.TopicDtos.TopicResponse;
import com.example.qldetai.feature.topic.TopicReviewDecision;
import com.example.qldetai.feature.topic.TopicService;
import com.example.qldetai.feature.topic.TopicStatus;
import com.example.qldetai.feature.topic.VisibilityType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class Backend2WorkflowTests {

    @Autowired
    private TopicService topicService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ProjectAssignmentService projectAssignmentService;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private RegistrationPeriodRepository periodRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private StudentGroupRepository groupRepository;

    @Autowired
    private TopicRegistrationRepository registrationRepository;

    @Autowired
    private ProjectAssignmentRepository assignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Faculty faculty;
    private Department department;
    private Semester semester;
    private RegistrationPeriod period;
    private User admin;
    private Lecturer lecturerOne;
    private Lecturer lecturerTwo;
    private Student leaderStudent;
    private Student secondStudent;
    private Student thirdStudent;
    private Student fourthStudent;

    @BeforeEach
    void setUp() {
        faculty = createFaculty("CNTT");
        department = createDepartment(faculty, "KTPM");
        semester = createSemester();
        admin = createUser("admin", "Backend 2 Admin", Set.of(RoleCode.ADMIN));
        lecturerOne = createLecturer("GV01", "Giảng viên 1");
        lecturerTwo = createLecturer("GV02", "Giảng viên 2");
        leaderStudent = createStudent("SV01", "Sinh viên trưởng nhóm");
        secondStudent = createStudent("SV02", "Sinh viên 2");
        thirdStudent = createStudent("SV03", "Sinh viên 3");
        fourthStudent = createStudent("SV04", "Sinh viên 4");
        period = createActivePeriod();
    }

    @Test
    void topicLifecycleCanGoFromProposalToPublished() {
        TopicResponse created = topicService.create(
                new CreateTopicRequest(
                        period.getId(),
                        "dt-01",
                        "Xây dựng API backend 2",
                        "Mô tả đề tài",
                        "Hoàn thiện các API nghiệp vụ",
                        "Có thể ghép với frontend",
                        "Biết Spring Boot",
                        VisibilityType.OPEN,
                        null),
                lecturerOne.getUser().getId());

        assertThat(created.code()).isEqualTo("DT-01");
        assertThat(created.status()).isEqualTo(TopicStatus.PENDING);
        assertThat(created.proposalSource().name()).isEqualTo("LECTURER");

        TopicResponse reviewed = topicService.review(
                created.id(),
                new ReviewTopicRequest(created.version(), TopicReviewDecision.APPROVED, "Đề tài đạt yêu cầu."),
                admin.getId());

        assertThat(reviewed.status()).isEqualTo(TopicStatus.APPROVED);
        assertThat(reviewed.latestReview()).isNotNull();
        assertThat(reviewed.latestReview().decision()).isEqualTo(TopicReviewDecision.APPROVED);

        TopicResponse assigned = topicService.assignSupervisors(
                reviewed.id(),
                new AssignSupervisorsRequest(reviewed.version(), lecturerOne.getId(), lecturerTwo.getId()),
                admin.getId());

        assertThat(assigned.supervisors()).hasSize(2);
        assertThat(assigned.supervisors().getFirst().slotNo()).isEqualTo(1);
        assertThat(assigned.supervisors().get(1).slotNo()).isEqualTo(2);

        TopicResponse published = topicService.publish(
                assigned.id(),
                new PublishTopicRequest(assigned.version()),
                admin.getId());

        assertThat(published.status()).isEqualTo(TopicStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.supervisors()).hasSize(2);
    }

    @Test
    void groupMemberSlotIsReusedAfterRemovingAMember() {
        StudentGroupResponse created = groupService.create(
                new CreateGroupRequest(period.getId(), "Nhóm backend 2"),
                leaderStudent.getUser().getId());

        groupService.addMember(
                created.id(),
                new AddGroupMemberRequest(secondStudent.getStudentCode()),
                leaderStudent.getUser().getId());
        groupService.addMember(
                created.id(),
                new AddGroupMemberRequest(thirdStudent.getStudentCode()),
                leaderStudent.getUser().getId());
        groupService.removeMember(created.id(), secondStudent.getId(), leaderStudent.getUser().getId());

        StudentGroupResponse updated = groupService.addMember(
                created.id(),
                new AddGroupMemberRequest(fourthStudent.getStudentCode()),
                leaderStudent.getUser().getId());

        List<GroupMember> activeMembers = groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(created.id());
        assertThat(updated.status()).isEqualTo(GroupStatus.ACTIVE);
        assertThat(activeMembers).hasSize(3);
        assertThat(activeMembers)
                .extracting(GroupMember::getSlotNo)
                .containsExactly(1, 2, 3);
        assertThat(activeMembers)
                .extracting(member -> member.getStudent().getStudentCode())
                .containsExactly(leaderStudent.getStudentCode(), fourthStudent.getStudentCode(), thirdStudent.getStudentCode());
    }

    @Test
    void topicRegistrationCanBeSubmittedAndApproved() {
        TopicResponse topic = createPublishedTopic("dt-reg-01");
        StudentGroupResponse group = createActiveGroup("Nhóm đăng ký", leaderStudent, secondStudent);

        TopicRegistrationResponse created = registrationService.create(
                new CreateTopicRegistrationRequest(group.id(), topic.id(), "Nhóm phù hợp với đề tài này."),
                leaderStudent.getUser().getId());

        assertThat(created.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(created.topicCode()).isEqualTo(topic.code());

        TopicRegistrationResponse reviewed = registrationService.review(
                created.id(),
                new ReviewTopicRegistrationRequest(created.version(), RegistrationStatus.APPROVED, null),
                admin.getId());

        assertThat(reviewed.status()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(reviewed.reviewedBy()).isEqualTo(admin.getId());
        assertThat(reviewed.decisionReason()).isNull();
    }

    @Test
    void assignmentCreationRejectsOtherPendingRegistrationAndCompletionClosesGroup() {
        TopicResponse topic = createPublishedTopic("dt-assign-01");
        StudentGroupResponse firstGroup = createActiveGroup("Nhóm A", leaderStudent, secondStudent);
        StudentGroupResponse secondGroup = createActiveGroup("Nhóm B", thirdStudent, fourthStudent);

        TopicRegistrationResponse approvedRegistration = registrationService.create(
                new CreateTopicRegistrationRequest(firstGroup.id(), topic.id(), "Nhóm A đăng ký."),
                leaderStudent.getUser().getId());
        approvedRegistration = registrationService.review(
                approvedRegistration.id(),
                new ReviewTopicRegistrationRequest(approvedRegistration.version(), RegistrationStatus.APPROVED, null),
                admin.getId());

        TopicRegistrationResponse pendingRegistration = registrationService.create(
                new CreateTopicRegistrationRequest(secondGroup.id(), topic.id(), "Nhóm B đăng ký."),
                thirdStudent.getUser().getId());

        ProjectAssignmentResponse created = projectAssignmentService.create(
                new CreateAssignmentRequest(approvedRegistration.id()),
                admin.getId());

        assertThat(created.status()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(assignmentRepository.existsByGroupId(firstGroup.id())).isTrue();
        assertThat(groupRepository.findById(firstGroup.id()).orElseThrow().getStatus()).isEqualTo(GroupStatus.LOCKED);

        TopicRegistration rejectedRegistration = registrationRepository.findById(pendingRegistration.id()).orElseThrow();
        assertThat(rejectedRegistration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);

        ProjectAssignmentResponse completed = projectAssignmentService.updateStatus(
                created.id(),
                new UpdateAssignmentStatusRequest(created.version(), AssignmentStatus.COMPLETED),
                admin.getId());

        assertThat(completed.status()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(groupRepository.findById(firstGroup.id()).orElseThrow().getStatus()).isEqualTo(GroupStatus.CLOSED);
    }

    private TopicResponse createPublishedTopic(String code) {
        TopicResponse created = topicService.create(
                new CreateTopicRequest(
                        period.getId(),
                        code,
                        "Đề tài " + code,
                        "Mô tả " + code,
                        "Mục tiêu " + code,
                        "Kết quả " + code,
                        null,
                        VisibilityType.OPEN,
                        null),
                lecturerOne.getUser().getId());
        TopicResponse reviewed = topicService.review(
                created.id(),
                new ReviewTopicRequest(created.version(), TopicReviewDecision.APPROVED, "Đạt."),
                admin.getId());
        TopicResponse assigned = topicService.assignSupervisors(
                reviewed.id(),
                new AssignSupervisorsRequest(reviewed.version(), lecturerOne.getId(), lecturerTwo.getId()),
                admin.getId());
        return topicService.publish(
                assigned.id(),
                new PublishTopicRequest(assigned.version()),
                admin.getId());
    }

    private StudentGroupResponse createActiveGroup(String name, Student leader, Student member) {
        StudentGroupResponse group = groupService.create(
                new CreateGroupRequest(period.getId(), name),
                leader.getUser().getId());
        return groupService.addMember(
                group.id(),
                new AddGroupMemberRequest(member.getStudentCode()),
                leader.getUser().getId());
    }

    private Faculty createFaculty(String baseCode) {
        Faculty entity = new Faculty();
        entity.setCode(unique(baseCode));
        entity.setName("Khoa " + baseCode);
        entity.setActive(true);
        return facultyRepository.save(entity);
    }

    private Department createDepartment(Faculty owner, String baseCode) {
        Department entity = new Department();
        entity.setFaculty(owner);
        entity.setCode(unique(baseCode));
        entity.setName("Bộ môn " + baseCode);
        entity.setActive(true);
        return departmentRepository.save(entity);
    }

    private Semester createSemester() {
        Semester entity = new Semester();
        entity.setAcademicYear("2026-2027");
        entity.setSemesterNo(1);
        entity.setStartsOn(LocalDate.of(2026, 9, 1));
        entity.setEndsOn(LocalDate.of(2027, 1, 31));
        return semesterRepository.save(entity);
    }

    private RegistrationPeriod createActivePeriod() {
        LocalDateTime now = nowUtc();
        RegistrationPeriod entity = new RegistrationPeriod();
        entity.setFaculty(faculty);
        entity.setSemester(semester);
        entity.setName("Đợt backend 2 " + unique("period"));
        entity.setProjectType(ProjectType.COURSE);
        entity.setProposalStartAt(now.minusDays(2));
        entity.setProposalEndAt(now.plusDays(2));
        entity.setRegistrationStartAt(now.minusDays(2));
        entity.setRegistrationEndAt(now.plusDays(2));
        entity.setReviewDeadlineAt(now.plusDays(7));
        entity.setDefenseDate(now.toLocalDate().plusDays(30));
        entity.setStatus(RegistrationPeriodStatus.ACTIVE);
        entity.setCreatedBy(admin);
        entity.setCreatedAt(now.minusHours(3));
        return periodRepository.save(entity);
    }

    private Lecturer createLecturer(String baseCode, String fullName) {
        User user = createUser(baseCode.toLowerCase(), fullName, Set.of(RoleCode.LECTURER));
        Lecturer entity = new Lecturer();
        entity.setUser(user);
        entity.setDepartment(department);
        entity.setLecturerCode(unique(baseCode));
        entity.setAcademicDegree("ThS");
        entity.setSpecialization("Spring Boot");
        return lecturerRepository.save(entity);
    }

    private Student createStudent(String baseCode, String fullName) {
        User user = createUser(baseCode.toLowerCase(), fullName, Set.of(RoleCode.STUDENT));
        Student entity = new Student();
        entity.setUser(user);
        entity.setDepartment(department);
        entity.setStudentCode(unique(baseCode));
        entity.setClassName("D21CQCN01");
        entity.setCohort("K21");
        return studentRepository.save(entity);
    }

    private User createUser(String emailPrefix, String fullName, Set<RoleCode> roleCodes) {
        LocalDateTime now = nowUtc();
        User entity = new User();
        entity.setEmail(unique(emailPrefix) + "@test.local");
        entity.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        entity.setFullName(fullName);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.replaceRoles(resolveRoles(roleCodes), now);
        return userRepository.save(entity);
    }

    private Set<Role> resolveRoles(Set<RoleCode> roleCodes) {
        return new LinkedHashSet<>(roleRepository.findAllByCodeIn(roleCodes));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    private String unique(String base) {
        return base + "-" + System.nanoTime();
    }
}
