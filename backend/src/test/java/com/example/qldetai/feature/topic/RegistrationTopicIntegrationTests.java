package com.example.qldetai.feature.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.academic.domain.*;
import com.example.qldetai.feature.academic.repository.*;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.service.GroupService;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.*;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateRegistrationTopicRequest;
import com.example.qldetai.feature.topic.repository.*;
import com.example.qldetai.feature.topic.service.TopicService;
import com.example.qldetai.security.UserPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationTopicIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private FacultyRepository faculties;
    @Autowired private DepartmentRepository departments;
    @Autowired private SemesterRepository semesters;
    @Autowired private RegistrationPeriodRepository periods;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private LecturerRepository lecturers;
    @Autowired private StudentRepository students;
    @Autowired private AcademicAppointmentRepository appointments;
    @Autowired private TopicRepository topics;
    @Autowired private TopicReviewRepository reviews;
    @Autowired private TopicSupervisorRepository supervisors;
    @Autowired private TopicService topicService;
    @Autowired private GroupService groupService;
    @Autowired private Clock clock;

    private Faculty faculty;
    private Department department;
    private Department siblingDepartment;
    private Department foreignDepartment;
    private RegistrationPeriod period;
    private User admin;
    private Lecturer manager;
    private Lecturer supervisor;
    private Lecturer foreignSupervisor;
    private Student student;
    private long topicsBefore;

    @BeforeEach
    void setUp() {
        topicsBefore = topics.count();
        faculty = faculty();
        department = department(faculty);
        siblingDepartment = department(faculty);
        foreignDepartment = department(faculty());
        admin = actor(RoleCode.ADMIN);
        manager = lecturer(department);
        supervisor = lecturer(siblingDepartment);
        foreignSupervisor = lecturer(foreignDepartment);
        student = new Student();
        student.setUser(actor(RoleCode.STUDENT));
        student.setDepartment(department);
        student.setStudentCode("S" + System.nanoTime());
        student.setClassName("Test class");
        student.setCohort("2026");
        students.save(student);

        LocalDateTime now = LocalDateTime.now(clock);
        Semester semester = new Semester();
        semester.setAcademicYear("2026-2027");
        semester.setSemesterNo(1);
        semester.setStartsOn(now.minusMonths(1).toLocalDate());
        semester.setEndsOn(now.plusMonths(4).toLocalDate());
        semester = semesters.findByAcademicYearIgnoreCaseAndSemesterNo("2026-2027", 1)
                .orElseGet(() -> {
                    Semester created = new Semester();
                    created.setAcademicYear("2026-2027");
                    created.setSemesterNo(1);
                    created.setStartsOn(now.minusMonths(1).toLocalDate());
                    created.setEndsOn(now.plusMonths(4).toLocalDate());
                    return semesters.save(created);
                });
        period = new RegistrationPeriod();
        period.setFaculty(faculty);
        period.setSemester(semester);
        period.setName("Registration topic test");
        period.setProjectType(ProjectType.GRADUATION);
        period.setProposalStartAt(now.minusDays(10));
        period.setProposalEndAt(now.minusDays(2));
        period.setRegistrationStartAt(now.minusDays(1));
        period.setRegistrationEndAt(now.plusDays(10));
        period.setReviewDeadlineAt(now.plusDays(20));
        period.setDefenseDate(now.plusDays(30).toLocalDate());
        period.setStatus(RegistrationPeriodStatus.ACTIVE);
        period.setCreatedBy(admin);
        period.setCreatedAt(now);
        periods.save(period);
    }

    @ParameterizedTest
    @EnumSource(PositionCode.class)
    void academicHeadPublishesTopicAndStudentCanRegister(PositionCode position) throws Exception {
        appoint(position);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(department, supervisor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("REG-01"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.visibilityType").value("OPEN"))
                .andExpect(jsonPath("$.data.supervisors[0].lecturerId").value(supervisor.getId()))
                .andExpect(jsonPath("$.data.latestReview.decision").value("APPROVED"));
        var topic = topics.findByPeriodId(period.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        mvc.perform(get("/api/v1/topics").with(user(UserPrincipal.from(student.getUser())))
                        .param("periodId", period.getId().toString()).param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(topic.getId()));

        var group = groupService.create(new CreateGroupRequest(period.getId(), "Nhóm đăng ký"), student.getUser().getId());
        mvc.perform(post("/api/v1/topic-registrations")
                        .with(user(UserPrincipal.from(student.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":%d,\"topicId\":%d}".formatted(group.id(), topic.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void deanCanCreateForAnotherDepartmentInTheSameFaculty() throws Exception {
        appoint(PositionCode.DEAN);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(siblingDepartment, supervisor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.departmentId").value(siblingDepartment.getId()));
    }

    @Test
    void departmentHeadCannotCreateForSiblingDepartment() throws Exception {
        appoint(PositionCode.DEPARTMENT_HEAD);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(siblingDepartment, supervisor)))
                .andExpect(status().isForbidden());
        assertThat(topics.count()).isEqualTo(topicsBefore);
    }

    @Test
    void deanCannotCreateOutsideFaculty() throws Exception {
        appoint(PositionCode.DEAN);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(foreignDepartment, foreignSupervisor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void endedAppointmentCannotCreateEvenInAnExistingSession() throws Exception {
        var appointment = appoint(PositionCode.DEAN);
        var principal = UserPrincipal.from(manager.getUser());
        appointment.setEndsAt(LocalDateTime.now(clock).minusMinutes(1));
        appointments.saveAndFlush(appointment);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(department, supervisor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void futureAppointmentDoesNotGrantCreationRights() throws Exception {
        var appointment = appoint(PositionCode.DEAN);
        appointment.setStartsAt(LocalDateTime.now(clock).plusDays(1));
        appointments.saveAndFlush(appointment);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(department, supervisor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordinaryLecturerAndStudentCannotUseManagerCreation() throws Exception {
        for (User actor : new User[] { manager.getUser(), student.getUser() }) {
            mvc.perform(post("/api/v1/topics/for-registration").with(user(UserPrincipal.from(actor))).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(payload(department, supervisor)))
                    .andExpect(status().isForbidden());
        }
        assertThatThrownBy(() -> topicService.createForRegistration(
                new CreateRegistrationTopicRequest(period.getId(), department.getId(), "SERVICE", "Title", "Description",
                        "Objectives", "Outcomes", null, supervisor.getId(), null), manager.getUser().getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminWithoutLecturerProfileCanCreateForManagedCatalog() throws Exception {
        mvc.perform(post("/api/v1/topics/for-registration").with(user(UserPrincipal.from(admin))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(siblingDepartment, supervisor)))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidSupervisorDoesNotLeaveAPartialTopicOrReview() throws Exception {
        appoint(PositionCode.DEAN);
        long beforeTopics = topics.count();
        long beforeReviews = reviews.count();
        long beforeSupervisors = supervisors.count();
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(department, foreignSupervisor)))
                .andExpect(status().isBadRequest());
        assertThat(topics.count()).isEqualTo(beforeTopics);
        assertThat(reviews.count()).isEqualTo(beforeReviews);
        assertThat(supervisors.count()).isEqualTo(beforeSupervisors);
    }

    @Test
    void duplicateTopicCodeIsRejected() throws Exception {
        appoint(PositionCode.DEAN);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/topics/for-registration")
                            .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(payload(department, supervisor)))
                    .andExpect(attempt == 0 ? status().isCreated() : status().isConflict());
        }
        assertThat(topics.count()).isEqualTo(topicsBefore + 1);
    }

    @Test
    void closedRegistrationWindowIsRejected() throws Exception {
        appoint(PositionCode.DEAN);
        period.setRegistrationEndAt(LocalDateTime.now(clock).minusMinutes(1));
        periods.saveAndFlush(period);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(department, supervisor)))
                .andExpect(status().isBadRequest());
        assertThat(topics.count()).isEqualTo(topicsBefore);
    }

    @Test
    void inactivePeriodIsRejected() throws Exception {
        appoint(PositionCode.DEAN);
        period.setStatus(RegistrationPeriodStatus.CLOSED);
        periods.saveAndFlush(period);
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(department, supervisor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void primaryAndSecondarySupervisorsMustDiffer() throws Exception {
        appoint(PositionCode.DEAN);
        String request = payload(department, supervisor).replace("\"secondaryLecturerId\":null", "\"secondaryLecturerId\":" + supervisor.getId());
        mvc.perform(post("/api/v1/topics/for-registration")
                        .with(user(UserPrincipal.from(manager.getUser()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertThat(topics.count()).isEqualTo(topicsBefore);
    }

    private String payload(Department target, Lecturer primary) {
        return """
                {"periodId":%d,"departmentId":%d,"code":"reg-01","title":"Đề tài mở đăng ký",
                 "description":"Mô tả đề tài","objectives":"Mục tiêu","expectedOutcomes":"Kết quả",
                 "primaryLecturerId":%d,"secondaryLecturerId":null}
                """.formatted(period.getId(), target.getId(), primary.getId());
    }

    private AcademicAppointment appoint(PositionCode position) {
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(manager);
        appointment.setFaculty(faculty);
        appointment.setDepartment(position == PositionCode.DEPARTMENT_HEAD ? department : null);
        appointment.setPositionCode(position);
        appointment.setStartsAt(LocalDateTime.now(clock).minusDays(2));
        appointment.setAssignedBy(admin);
        return appointments.saveAndFlush(appointment);
    }

    private Faculty faculty() {
        Faculty entity = new Faculty();
        entity.setCode("F" + System.nanoTime());
        entity.setName("Khoa kiểm thử");
        return faculties.save(entity);
    }

    private Department department(Faculty parent) {
        Department entity = new Department();
        entity.setFaculty(parent);
        entity.setCode("D" + System.nanoTime());
        entity.setName("Bộ môn kiểm thử");
        return departments.save(entity);
    }

    private Lecturer lecturer(Department parent) {
        Lecturer entity = new Lecturer();
        entity.setUser(actor(RoleCode.LECTURER));
        entity.setDepartment(parent);
        entity.setLecturerCode("G" + System.nanoTime());
        return lecturers.save(entity);
    }

    private User actor(RoleCode role) {
        LocalDateTime now = LocalDateTime.now(clock);
        User entity = new User();
        entity.setEmail("creation-" + System.nanoTime() + "@test.local");
        entity.setFullName("Topic creation test");
        entity.setPasswordHash("unused-in-mock-authentication");
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.replaceRoles(Set.of(roles.findByCode(role).orElseThrow()), now);
        return users.save(entity);
    }
}
