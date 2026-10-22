package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
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

@SpringBootTest(properties = {"server.forward-headers-strategy=none", "app.security.trusted-proxies=127.0.0.1"})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
class AuthIntegrationTests {

    @Test
    void loginReturns429AfterFiveFailuresForTheSameUsernameAndIp() throws Exception {
        String email = createUserWithRole("throttled-login", RoleCode.ADMIN);
        for (int index = 0; index < 5; index++) {
            loginAttempt(email, "incorrect", "203.0.113.10").andExpect(status().isUnauthorized());
        }
        loginAttempt(email.toUpperCase(), "TestPassword123!", "203.0.113.10")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút."));
        loginAttempt(email, "TestPassword123!", "203.0.113.11").andExpect(status().isOk());
        loginAttempt("another-" + email, "incorrect", "203.0.113.10").andExpect(status().isUnauthorized());
    }

    @Test
    void successfulLoginResetsFailureCountForItsPair() throws Exception {
        String email = createUserWithRole("reset-login-limit", RoleCode.ADMIN);
        for (int index = 0; index < 4; index++) {
            loginAttempt(email, "incorrect", "203.0.113.12").andExpect(status().isUnauthorized());
        }
        loginAttempt(email, "TestPassword123!", "203.0.113.12").andExpect(status().isOk());
        for (int index = 0; index < 5; index++) {
            loginAttempt(email, "incorrect", "203.0.113.12").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void oversizedLoginPasswordReturnsClearVietnamese400() throws Exception {
        for (String password : new String[] {"A1!" + "a".repeat(70), "A1!" + "ề".repeat(24),
                "A1!" + "😀".repeat(18), "A1!" + "a".repeat(97)}) {
            loginAttempt("admin@test.local", password, "203.0.113.80")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(com.example.qldetai.feature.identity.validation.PasswordByteLength.MESSAGE));
        }
    }

    @Test
    void legacyPbkdf2UserCanStillLogin() throws Exception {
        String email = createUserWithRole("legacy-pbkdf2", RoleCode.ADMIN);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setPasswordHash("{pbkdf2}" + org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
                .defaultsForSpringSecurity_v5_8().encode("TestPassword123!"));
        userRepository.saveAndFlush(user);
        loginAttempt(email, "TestPassword123!", "203.0.113.81").andExpect(status().isOk());
    }

    @Test
    void longLegacyPbkdf2PasswordsAuthenticateWithoutChangingStoredCredentials() throws Exception {
        int index = 0;
        for (String password : new String[] {"A1!" + "a".repeat(97), "Đ1!" + "ề".repeat(30),
                "A1!" + "😀".repeat(20)}) {
            String email = createUserWithRole("long-legacy-" + index++, RoleCode.ADMIN);
            User legacy = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            String hash = "{pbkdf2}" + org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
                    .defaultsForSpringSecurity_v5_8().encode(password);
            legacy.setPasswordHash(hash);
            userRepository.saveAndFlush(legacy);
            loginAttempt(email, password, "203.0.113.82").andExpect(status().isOk());
            loginAttempt(email, password + "wrong", "203.0.113.82").andExpect(status().isUnauthorized());
            assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash().equals(hash)).isTrue();
        }
    }

    private org.springframework.test.web.servlet.ResultActions loginAttempt(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;
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
            StudentRepository studentRepository,
            AcademicAppointmentRepository academicAppointmentRepository) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.lecturerRepository = lecturerRepository;
        this.studentRepository = studentRepository;
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
                        .header("X-Forwarded-For", "203.0.113.42, 10.0.0.1")
                        .header("X-Real-IP", "172.18.0.1")
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].lastLoginIp").value("203.0.113.42"));
    }

    @Test
    void loginRecordsForwardedIpv6InsteadOfDockerPeerAddress() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .header("X-Forwarded-For", "2001:db8::42, 10.0.0.1")
                        .header("X-Real-IP", "172.18.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow()
                .getLastLoginIp()).isEqualTo("2001:db8::42");
    }

    @Test
    void directLoginRecordsRemoteAddressInsteadOfUnprocessedRealIpHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .with(request -> { request.setRemoteAddr("192.168.1.42"); return request; })
                        .header("X-Real-IP", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin@test.local","password":"TestPassword123!"}
                                """))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow()
                .getLastLoginIp()).isEqualTo("192.168.1.42");
    }

    @Test
    void authenticatedUserCanLoadDashboardSummary() throws Exception {
        MockHttpSession session = login("admin@test.local", "TestPassword123!");

        mockMvc.perform(get("/api/v1/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTopics").value(0))
                .andExpect(jsonPath("$.data.unreadNotifications").value(0));
        mockMvc.perform(post("/api/v1/evaluation-assignments/batch").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assignments\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotReadGroupOrCommitteeDirectories() throws Exception {
        String studentEmail = createUserWithRole("student-directory", RoleCode.STUDENT);
        MockHttpSession session = login(studentEmail, "TestPassword123!");

        mockMvc.perform(get("/api/v1/groups").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/evaluation-committees").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/defense-sessions/eligibility").param("assignmentId", "1").param("committeeId", "1").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/evaluation-assignments/batch").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"assignments":[{"assignmentId":1,"rubricId":1,"lecturerId":1,"deadlineAt":"2099-01-01T00:00:00"}]}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastActiveAdministratorCannotBeLockedOrLoseAdminRoleThroughOtherEndpoints() throws Exception {
        User admin = userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        assertThat(userRepository.findActiveAdministrators()).hasSize(1);
        MockHttpSession session = login("admin@test.local", "TestPassword123!");
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", admin.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/users/{id}/roles", admin.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"LECTURER\"]}"))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findActiveAdministrators()).hasSize(1);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
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

        mockMvc.perform(get("/api/v1/lecturers").session(session)
                        .param("lecturerId", matched.getId().toString()).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matched.getId()));
        mockMvc.perform(get("/api/v1/lecturers").session(session)
                        .param("lecturerId", matched.getId().toString())
                        .param("departmentId", otherDepartment.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/lecturers").session(session)
                        .param("lecturerId", locked.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
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

    @Test
    void adminCanDeleteAnotherAccountWithoutRemovingHistoricalData() throws Exception {
        String targetEmail = createUserWithRole("delete-target", RoleCode.STUDENT);
        User target = userRepository.findByEmailIgnoreCase(targetEmail).orElseThrow();
        MockHttpSession session = login("admin@test.local", "TestPassword123!");

        mockMvc.perform(delete("/api/v1/admin/users/{id}", target.getId())
                        .with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.DISABLED);
        mockMvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminCanUpdateAccountAndStudentProfile() throws Exception {
        Faculty faculty = createFaculty("UPDATE");
        Department oldDepartment = createDepartment(faculty, "OLD");
        Department newDepartment = createDepartment(faculty, "NEW");
        Student student = createStudent("update-target", "SVOLD", "Sinh Vien Cu", oldDepartment);
        User target = student.getUser();
        MockHttpSession session = login("admin@test.local", "TestPassword123!");

        mockMvc.perform(put("/api/v1/admin/users/{id}", target.getId())
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "email": "sinhvien.capnhat@test.local",
                                  "fullName": "Sinh Vien Da Cap Nhat",
                                  "phone": "0909000000",
                                  "student": {
                                    "departmentId": %d,
                                    "studentCode": "SVMOI",
                                    "className": "DHKTPM18A",
                                    "cohort": "2026"
                                  },
                                  "lecturer": null
                                }
                                """.formatted(target.getVersion(), newDepartment.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("sinhvien.capnhat@test.local"))
                .andExpect(jsonPath("$.data.fullName").value("Sinh Vien Da Cap Nhat"))
                .andExpect(jsonPath("$.data.student.studentCode").value("SVMOI"))
                .andExpect(jsonPath("$.data.student.departmentId").value(newDepartment.getId()));

        User updatedUser = userRepository.findById(target.getId()).orElseThrow();
        Student updatedStudent = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(updatedUser.getPhone()).isEqualTo("0909000000");
        assertThat(updatedStudent.getClassName()).isEqualTo("DHKTPM18A");
        assertThat(updatedStudent.getCohort()).isEqualTo("2026");
        assertThat(updatedStudent.getDepartment().getId()).isEqualTo(newDepartment.getId());
    }

    @Test
    void adminCannotDeleteOwnAccount() throws Exception {
        User admin = userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow();
        MockHttpSession session = login("admin@test.local", "TestPassword123!");

        mockMvc.perform(delete("/api/v1/admin/users/{id}", admin.getId())
                        .with(csrf())
                        .session(session))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
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

    private Student createStudent(String emailPrefix, String studentCodePrefix, String fullName, Department department) {
        Role studentRole = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);

        User user = new User();
        user.setEmail(emailPrefix + "@test.local");
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(Set.of(studentRole), now);
        user = userRepository.saveAndFlush(user);

        Student student = new Student();
        student.setUser(user);
        student.setDepartment(department);
        student.setStudentCode(uniqueCode(studentCodePrefix, 20));
        student.setClassName("DHKTPM17A");
        student.setCohort("2025");
        return studentRepository.saveAndFlush(student);
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
