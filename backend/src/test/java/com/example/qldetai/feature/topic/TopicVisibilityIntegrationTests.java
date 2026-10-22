package com.example.qldetai.feature.topic;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.topic.domain.ProposalSource;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.domain.VisibilityType;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import com.example.qldetai.security.UserPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class TopicVisibilityIntegrationTests {

    private final MockMvc mockMvc;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicRepository topicRepository;
    private final Clock clock;

    private Department department;
    private RegistrationPeriod period;
    private Student currentStudent;
    private Student otherStudent;
    private StudentGroup currentStudentGroup;
    private StudentGroup otherStudentGroup;
    private Topic publishedOpen;
    private Topic publishedReservedForCurrentStudent;
    private Topic publishedReservedForOtherStudent;
    private Topic pendingOwn;
    private Topic pendingOther;

    TopicVisibilityIntegrationTests(
            MockMvc mockMvc,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            SemesterRepository semesterRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            StudentGroupRepository studentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            TopicRepository topicRepository,
            Clock clock) {
        this.mockMvc = mockMvc;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.topicRepository = topicRepository;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        LocalDateTime now = LocalDateTime.now(clock);

        Faculty faculty = new Faculty();
        faculty.setCode("F" + suffix);
        faculty.setName("Khoa hiển thị " + suffix);
        faculty.setActive(true);
        faculty = facultyRepository.save(faculty);

        department = new Department();
        department.setFaculty(faculty);
        department.setCode("BM" + suffix);
        department.setName("Bộ môn hiển thị " + suffix);
        department.setActive(true);
        department = departmentRepository.save(department);

        Semester semester = new Semester();
        semester.setAcademicYear("2099-2100");
        semester.setSemesterNo(1);
        semester.setStartsOn(LocalDate.of(2099, 1, 1));
        semester.setEndsOn(LocalDate.of(2099, 6, 30));
        semester = semesterRepository.save(semester);

        currentStudent = createStudent("viewer-" + suffix, "Sinh viên đang xem", now);
        otherStudent = createStudent("other-" + suffix, "Sinh viên khác", now);

        period = new RegistrationPeriod();
        period.setFaculty(faculty);
        period.setSemester(semester);
        period.setName("Đợt hiển thị " + suffix);
        period.setProjectType(ProjectType.COURSE);
        period.setProposalStartAt(now.minusDays(1));
        period.setProposalEndAt(now.plusDays(1));
        period.setRegistrationStartAt(now.minusDays(1));
        period.setRegistrationEndAt(now.plusDays(1));
        period.setStatus(RegistrationPeriodStatus.ACTIVE);
        period.setCreatedBy(currentStudent.getUser());
        period.setCreatedAt(now);
        period = registrationPeriodRepository.save(period);

        currentStudentGroup = createGroup("Nhóm của người xem " + suffix, currentStudent, now);
        otherStudentGroup = createGroup("Nhóm khác " + suffix, otherStudent, now);
        publishedOpen =
                createTopic(
                        "OPEN-" + suffix,
                        otherStudent.getUser(),
                        VisibilityType.OPEN,
                        null,
                        TopicStatus.PUBLISHED,
                        now);
        publishedReservedForCurrentStudent =
                createTopic(
                        "OWN-GROUP-" + suffix,
                        otherStudent.getUser(),
                        VisibilityType.RESERVED,
                        currentStudentGroup,
                        TopicStatus.PUBLISHED,
                        now);
        publishedReservedForOtherStudent =
                createTopic(
                        "OTHER-GROUP-" + suffix,
                        otherStudent.getUser(),
                        VisibilityType.RESERVED,
                        otherStudentGroup,
                        TopicStatus.PUBLISHED,
                        now);
        pendingOwn =
                createTopic(
                        "OWN-PENDING-" + suffix,
                        currentStudent.getUser(),
                        VisibilityType.OPEN,
                        null,
                        TopicStatus.PENDING,
                        now);
        pendingOther =
                createTopic(
                        "OTHER-PENDING-" + suffix,
                        otherStudent.getUser(),
                        VisibilityType.OPEN,
                        null,
                        TopicStatus.PENDING,
                        now);
    }

    @Test
    void studentSeesOnlyOwnAndEligiblePublishedTopics() throws Exception {
        mockMvc.perform(
                        get("/api/v1/topics")
                                .with(user(UserPrincipal.from(currentStudent.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(
                        jsonPath(
                                "$.data.content[*].code",
                                containsInAnyOrder(
                                        publishedOpen.getCode(),
                                        publishedReservedForCurrentStudent.getCode(),
                                        pendingOwn.getCode())));

        mockMvc.perform(
                        get("/api/v1/topics")
                                .param("status", "PENDING")
                                .with(user(UserPrincipal.from(currentStudent.getUser()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value(pendingOwn.getCode()));

        mockMvc.perform(
                        get("/api/v1/topics/{id}", publishedReservedForCurrentStudent.getId())
                                .with(user(UserPrincipal.from(currentStudent.getUser()))))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/topics/{id}", publishedReservedForOtherStudent.getId())
                                .with(user(UserPrincipal.from(currentStudent.getUser()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/v1/topics/{id}", pendingOther.getId())
                                .with(user(UserPrincipal.from(currentStudent.getUser()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotReserveTopicForAnotherGroup() throws Exception {
        String request =
                """
                {
                  "periodId": %d,
                  "code": "RESERVED-%s",
                  "title": "Đề tài dành riêng sai nhóm",
                  "description": "Mô tả kiểm thử",
                  "objectives": "Mục tiêu kiểm thử",
                  "expectedOutcomes": "Kết quả kiểm thử",
                  "visibilityType": "RESERVED",
                  "reservedGroupId": %d
                }
                """
                        .formatted(
                                period.getId(),
                                Long.toUnsignedString(System.nanoTime(), 36),
                                otherStudentGroup.getId());

        mockMvc.perform(
                        post("/api/v1/topics")
                                .with(user(UserPrincipal.from(currentStudent.getUser())))
                                .with(csrf())
                                .contentType("application/json")
                                .content(request))
                .andExpect(status().isForbidden());
    }

    private Student createStudent(String prefix, String fullName, LocalDateTime now) {
        Role role = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();
        User user = new User();
        user.setEmail(prefix + "@test.local");
        user.setPasswordHash("test-password");
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(role), now);
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setDepartment(department);
        student.setStudentCode(prefix.toUpperCase());
        student.setClassName("D21CQCN01");
        student.setCohort("K21");
        return studentRepository.save(student);
    }

    private StudentGroup createGroup(String name, Student leader, LocalDateTime now) {
        StudentGroup group = new StudentGroup();
        group.setPeriod(period);
        group.setName(name);
        group.setStatus(GroupStatus.ACTIVE);
        group.setCreatedBy(leader);
        group.setCreatedAt(now);
        group = studentGroupRepository.save(group);

        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setPeriod(period);
        member.setStudent(leader);
        member.setSlotNo(1);
        member.setMemberRole(GroupMemberRole.LEADER);
        member.setJoinedAt(now);
        groupMemberRepository.save(member);
        return group;
    }

    private Topic createTopic(
            String code,
            User proposer,
            VisibilityType visibilityType,
            StudentGroup reservedGroup,
            TopicStatus status,
            LocalDateTime now) {
        Topic topic = new Topic();
        topic.setPeriod(period);
        topic.setFaculty(period.getFaculty());
        topic.setDepartment(department);
        topic.setCode(code);
        topic.setTitle("Đề tài " + code);
        topic.setDescription("Mô tả " + code);
        topic.setObjectives("Mục tiêu " + code);
        topic.setExpectedOutcomes("Kết quả " + code);
        topic.setProposedBy(proposer);
        topic.setProposalSource(ProposalSource.STUDENT);
        topic.setVisibilityType(visibilityType);
        topic.setReservedGroup(reservedGroup);
        topic.setStatus(status);
        topic.setPublishedAt(status == TopicStatus.PUBLISHED ? now : null);
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        return topicRepository.saveAndFlush(topic);
    }
}
