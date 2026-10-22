package com.example.qldetai.feature.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateRegistrationTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.TopicResponse;
import com.example.qldetai.feature.topic.service.TopicService;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class RegistrationTopicCreationServiceTests {

    private final TopicService topicService;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final MockMvc mockMvc;
    private final Clock clock;

    private Department department;
    private RegistrationPeriod period;
    private User manager;
    private Lecturer primaryLecturer;
    private Lecturer secondaryLecturer;

    RegistrationTopicCreationServiceTests(
            TopicService topicService,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            SemesterRepository semesterRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            UserRepository userRepository,
            LecturerRepository lecturerRepository,
            AcademicAppointmentRepository academicAppointmentRepository,
            MockMvc mockMvc,
            Clock clock) {
        this.topicService = topicService;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.userRepository = userRepository;
        this.lecturerRepository = lecturerRepository;
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.mockMvc = mockMvc;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        LocalDateTime now = LocalDateTime.now(clock);

        Faculty faculty = new Faculty();
        faculty.setCode("F" + suffix);
        faculty.setName("Khoa kiểm thử " + suffix);
        faculty.setActive(true);
        faculty = facultyRepository.save(faculty);

        department = new Department();
        department.setFaculty(faculty);
        department.setCode("BM" + suffix);
        department.setName("Bộ môn kiểm thử " + suffix);
        department.setActive(true);
        department = departmentRepository.save(department);

        Semester semester = new Semester();
        semester.setAcademicYear("2099-2100");
        semester.setSemesterNo(1);
        semester.setStartsOn(LocalDate.of(2099, 1, 1));
        semester.setEndsOn(LocalDate.of(2099, 6, 30));
        semester = semesterRepository.save(semester);

        manager = createUser("manager-" + suffix, "Quản lý bộ môn", now);
        primaryLecturer = createLecturer("primary-" + suffix, "GV hướng dẫn chính", now);
        secondaryLecturer = createLecturer("secondary-" + suffix, "GV đồng hướng dẫn", now);

        period = new RegistrationPeriod();
        period.setFaculty(faculty);
        period.setSemester(semester);
        period.setName("Đợt kiểm thử " + suffix);
        period.setProjectType(ProjectType.COURSE);
        period.setProposalStartAt(now.minusDays(1));
        period.setProposalEndAt(now.plusDays(1));
        period.setRegistrationStartAt(now.plusDays(1));
        period.setRegistrationEndAt(now.plusDays(10));
        period.setStatus(RegistrationPeriodStatus.ACTIVE);
        period.setCreatedBy(manager);
        period.setCreatedAt(now);
        period = registrationPeriodRepository.save(period);
    }

    @Test
    void createsAndPublishesTopicForSelectedDepartmentWithTwoSupervisors() {
        TopicResponse created =
                topicService.createForRegistration(
                        new CreateRegistrationTopicRequest(
                                period.getId(),
                                department.getId(),
                                "TOPIC-" + Long.toUnsignedString(System.nanoTime(), 36),
                                "Đề tài kiểm thử",
                                "Mô tả rõ ràng",
                                "Mục tiêu kiểm thử",
                                "Kết quả kiểm thử",
                                null,
                                primaryLecturer.getId(),
                                secondaryLecturer.getId()),
                        manager.getId());

        assertThat(created.departmentId()).isEqualTo(department.getId());
        assertThat(created.status()).isEqualTo(TopicStatus.PUBLISHED);
        assertThat(created.availableForRegistration()).isTrue();
        assertThat(created.supervisors())
                .extracting(supervisor -> supervisor.lecturerId())
                .containsExactly(primaryLecturer.getId(), secondaryLecturer.getId());
    }

    @Test
    void departmentHeadCanCreateTopicForRegistrationWhileUnrelatedUserCannot() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(primaryLecturer);
        appointment.setFaculty(department.getFaculty());
        appointment.setDepartment(department);
        appointment.setPositionCode(PositionCode.DEPARTMENT_HEAD);
        appointment.setStartsAt(now.minusDays(1));
        appointment.setAssignedBy(manager);
        academicAppointmentRepository.saveAndFlush(appointment);

        String request =
                registrationTopicRequest("API-" + Long.toUnsignedString(System.nanoTime(), 36));
        mockMvc.perform(
                        post("/api/v1/topics/for-registration")
                                .with(user(UserPrincipal.from(manager)))
                                .with(csrf())
                                .contentType("application/json")
                                .content(request))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/topics/for-registration")
                                .with(user(UserPrincipal.from(primaryLecturer.getUser())))
                                .with(csrf())
                                .contentType("application/json")
                                .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.departmentId").value(department.getId()))
                .andExpect(jsonPath("$.data.availableForRegistration").value(true))
                .andExpect(jsonPath("$.data.supervisors.length()").value(2));
    }

    private String registrationTopicRequest(String code) {
        return """
                {
                  "periodId": %d,
                  "departmentId": %d,
                  "code": "%s",
                  "title": "Đề tài qua API",
                  "description": "Mô tả rõ ràng",
                  "objectives": "Mục tiêu kiểm thử",
                  "expectedOutcomes": "Kết quả kiểm thử",
                  "primaryLecturerId": %d,
                  "secondaryLecturerId": %d
                }
                """
                .formatted(
                        period.getId(),
                        department.getId(),
                        code,
                        primaryLecturer.getId(),
                        secondaryLecturer.getId());
    }

    private User createUser(String emailPrefix, String fullName, LocalDateTime now) {
        User user = new User();
        user.setEmail(emailPrefix + "@test.local");
        user.setPasswordHash("test-password");
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private Lecturer createLecturer(String code, String fullName, LocalDateTime now) {
        Lecturer lecturer = new Lecturer();
        lecturer.setUser(createUser(code, fullName, now));
        lecturer.setDepartment(department);
        lecturer.setLecturerCode(code);
        return lecturerRepository.save(lecturer);
    }
}
