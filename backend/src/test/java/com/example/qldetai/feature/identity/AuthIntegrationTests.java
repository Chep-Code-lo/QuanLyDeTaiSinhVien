package com.example.qldetai.feature.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTests {

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final LecturerRepository lecturerRepository;
    private final AcademicAppointmentRepository academicAppointmentRepository;

    AuthIntegrationTests(
            MockMvc mockMvc,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            LecturerRepository lecturerRepository,
            AcademicAppointmentRepository academicAppointmentRepository) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.lecturerRepository = lecturerRepository;
        this.academicAppointmentRepository = academicAppointmentRepository;
    }

    @BeforeEach
    void createAdmin() {
        if (userRepository.findByEmailIgnoreCase("admin@test.local").isPresent()) {
            return;
        }
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setEmail("admin@test.local");
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setFullName("Test Admin");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(adminRole), now);
        userRepository.saveAndFlush(user);
    }

    @Test
    void loginCreatesSessionAndAllowsAdminAccess() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.username").value("admin@test.local"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Test Admin"));
        mockMvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCanLoadDashboardSummary() throws Exception {
        MockHttpSession session = login("admin@test.local", "TestPassword123!");

        mockMvc.perform(get("/api/v1/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTopics").value(0))
                .andExpect(jsonPath("$.data.unreadNotifications").value(0));
    }

    @Test
    void studentCannotReadGroupOrCommitteeDirectories() throws Exception {
        String studentEmail = createUserWithRole("student-directory", RoleCode.STUDENT);
        MockHttpSession session = login(studentEmail, "TestPassword123!");

        mockMvc.perform(get("/api/v1/groups").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/evaluation-committees").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void currentUserIncludesActiveAppointmentsForLecturer() throws Exception {
        Faculty faculty = createFaculty("CNTT");
        Department department = createDepartment(faculty, "CNPM");
        Lecturer lecturer = createLecturer("lecturer-me", "GVME", "Giang Vien Co Nhiem Vu", department);
        createAppointment(lecturer, faculty, department, PositionCode.DEPARTMENT_HEAD);

        MockHttpSession session = login("lecturer-me@test.local", "TestPassword123!");

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Giang Vien Co Nhiem Vu"))
                .andExpect(jsonPath("$.data.lecturer.lecturerCode").value(lecturer.getLecturerCode()))
                .andExpect(jsonPath("$.data.activeAppointments[0].lecturerId").value(lecturer.getId()))
                .andExpect(jsonPath("$.data.activeAppointments[0].facultyId").value(faculty.getId()))
                .andExpect(jsonPath("$.data.activeAppointments[0].departmentId").value(department.getId()))
                .andExpect(jsonPath("$.data.activeAppointments[0].positionCode").value("DEPARTMENT_HEAD"));
    }

    @Test
    void lecturerDirectoryFiltersBySearchAndDepartment() throws Exception {
        Faculty faculty = createFaculty("QTDL");
        Department targetDepartment = createDepartment(faculty, "HTTT");
        Department otherDepartment = createDepartment(faculty, "KHMT");

        Lecturer matched = createLecturer("directory-match", "GVSEARCH", "Nguyen Van Search", targetDepartment);
        createLecturer("directory-other", "GVOTHER", "Tran Thi Search", otherDepartment);
        Lecturer locked = createLecturer("directory-locked", "GVLOCK", "Le Search Locked", targetDepartment);
        locked.getUser().setStatus(UserStatus.LOCKED);
        userRepository.saveAndFlush(locked.getUser());

        MockHttpSession session = login("admin@test.local", "TestPassword123!");

        mockMvc.perform(get("/api/v1/lecturers")
                        .session(session)
                        .param("search", "search")
                        .param("departmentId", targetDepartment.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matched.getId()))
                .andExpect(jsonPath("$.data.content[0].lecturerCode").value(matched.getLecturerCode()))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Nguyen Van Search"))
                .andExpect(jsonPath("$.data.content[0].facultyId").value(faculty.getId()))
                .andExpect(jsonPath("$.data.content[0].departmentId").value(targetDepartment.getId()));
    }

    @Test
    void stateChangingRequestWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void revokedAdminRoleTakesEffectForExistingSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        User user = userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        Role studentRole = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();
        user.replaceRoles(Set.of(studentRole), LocalDateTime.now(clock));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String createUserWithRole(String emailPrefix, RoleCode roleCode) {
        String email = emailPrefix + "-" + System.nanoTime() + "@test.local";
        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setFullName("Test " + roleCode.name());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(roleRepository.findByCode(roleCode).orElseThrow()), now);
        userRepository.saveAndFlush(user);
        return email;
    }

    private Faculty createFaculty(String baseCode) {
        Faculty faculty = new Faculty();
        faculty.setCode(uniqueCode(baseCode, 20));
        faculty.setName("Khoa " + baseCode);
        faculty.setActive(true);
        return facultyRepository.saveAndFlush(faculty);
    }

    private Department createDepartment(Faculty faculty, String baseCode) {
        Department department = new Department();
        department.setFaculty(faculty);
        department.setCode(uniqueCode(baseCode, 20));
        department.setName("Bo mon " + baseCode);
        department.setActive(true);
        return departmentRepository.saveAndFlush(department);
    }

    private Lecturer createLecturer(String emailPrefix, String lecturerCodePrefix, String fullName, Department department) {
        Role lecturerRole = roleRepository.findByCode(RoleCode.LECTURER).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);

        User user = new User();
        user.setEmail(emailPrefix + "@test.local");
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(lecturerRole), now);
        user = userRepository.saveAndFlush(user);

        Lecturer lecturer = new Lecturer();
        lecturer.setUser(user);
        lecturer.setDepartment(department);
        lecturer.setLecturerCode(uniqueCode(lecturerCodePrefix, 20));
        lecturer.setAcademicDegree("TS");
        lecturer.setSpecialization("Software Engineering");
        return lecturerRepository.saveAndFlush(lecturer);
    }

    private void createAppointment(Lecturer lecturer, Faculty faculty, Department department, PositionCode positionCode) {
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(lecturer);
        appointment.setFaculty(faculty);
        appointment.setDepartment(department);
        appointment.setPositionCode(positionCode);
        appointment.setStartsAt(LocalDateTime.now(clock).minusDays(1));
        appointment.setEndsAt(LocalDateTime.now(clock).plusDays(1));
        appointment.setAssignedBy(userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow());
        academicAppointmentRepository.saveAndFlush(appointment);
    }

    private String unique(String base) {
        return base + "-" + System.nanoTime();
    }

    private String uniqueCode(String base, int maxLength) {
        String suffix = Long.toString(System.nanoTime());
        int prefixLength = Math.max(1, maxLength - suffix.length() - 1);
        String prefix = base.length() > prefixLength ? base.substring(0, prefixLength) : base;
        return prefix + "-" + suffix;
    }
}
