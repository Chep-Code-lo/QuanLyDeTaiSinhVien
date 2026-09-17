package com.example.qldetai.config;

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
import com.example.qldetai.feature.communication.NotificationService;
import com.example.qldetai.feature.identity.AcademicAppointment;
import com.example.qldetai.feature.identity.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.Lecturer;
import com.example.qldetai.feature.identity.LecturerRepository;
import com.example.qldetai.feature.identity.PositionCode;
import com.example.qldetai.feature.identity.Role;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.RoleRepository;
import com.example.qldetai.feature.identity.Student;
import com.example.qldetai.feature.identity.StudentRepository;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.identity.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private final String demoPassword;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;
    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(
            @Value("${app.demo-data.password}") String demoPassword,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            SemesterRepository semesterRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            LecturerRepository lecturerRepository,
            StudentRepository studentRepository,
            AcademicAppointmentRepository academicAppointmentRepository,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder) {
        this.demoPassword = demoPassword;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.lecturerRepository = lecturerRepository;
        this.studentRepository = studentRepository;
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.length() < 8) {
            throw new IllegalStateException("DEMO_PASSWORD must contain at least 8 characters when demo data is enabled.");
        }

        Faculty faculty = facultyRepository.findByCodeIgnoreCase("CNTT").orElseGet(() -> {
            Faculty created = new Faculty();
            created.setCode("CNTT");
            created.setName("Công nghệ thông tin");
            created.setActive(true);
            return facultyRepository.save(created);
        });
        Department department = departmentRepository.findByFacultyIdAndCodeIgnoreCase(faculty.getId(), "CNPM")
                .orElseGet(() -> {
                    Department created = new Department();
                    created.setFaculty(faculty);
                    created.setCode("CNPM");
                    created.setName("Công nghệ phần mềm");
                    created.setActive(true);
                    return departmentRepository.save(created);
                });
        Semester semester = semesterRepository.findByAcademicYearIgnoreCaseAndSemesterNo("2026-2027", 1)
                .orElseGet(() -> {
                    Semester created = new Semester();
                    created.setAcademicYear("2026-2027");
                    created.setSemesterNo(1);
                    created.setStartsOn(LocalDate.of(2026, Month.SEPTEMBER, 1));
                    created.setEndsOn(LocalDate.of(2027, Month.JANUARY, 31));
                    return semesterRepository.save(created);
                });

        User admin = ensureUser("admin@demo.local", "Quản trị demo", Set.of(RoleCode.ADMIN));
        User lecturerUser = ensureUser("lecturer@demo.local", "Giảng viên demo", Set.of(RoleCode.LECTURER));
        Lecturer lecturer = lecturerRepository.findByUserId(lecturerUser.getId()).orElseGet(() -> {
            Lecturer created = new Lecturer();
            created.setUser(lecturerUser);
            created.setDepartment(department);
            created.setLecturerCode("GV001");
            created.setAcademicDegree("ThS");
            created.setSpecialization("Công nghệ phần mềm");
            return lecturerRepository.save(created);
        });
        User studentUser = ensureUser("student@demo.local", "Sinh viên demo", Set.of(RoleCode.STUDENT));
        studentRepository.findByUserId(studentUser.getId()).orElseGet(() -> {
            Student created = new Student();
            created.setUser(studentUser);
            created.setDepartment(department);
            created.setStudentCode("SV001");
            created.setClassName("DHTI18A1HN");
            created.setCohort("K18");
            return studentRepository.save(created);
        });

        if (!academicAppointmentRepository.hasActiveFacultyAppointment(
                lecturerUser.getId(), PositionCode.DEAN, faculty.getId(), LocalDateTime.now())) {
            AcademicAppointment appointment = new AcademicAppointment();
            appointment.setLecturer(lecturer);
            appointment.setFaculty(faculty);
            appointment.setPositionCode(PositionCode.DEAN);
            appointment.setStartsAt(LocalDateTime.of(2026, Month.SEPTEMBER, 1, 0, 0));
            appointment.setAssignedBy(admin);
            academicAppointmentRepository.save(appointment);
        }

        if (!registrationPeriodRepository.existsByFacultyIdAndNameIgnoreCase(
                faculty.getId(), "Đợt KLTN demo 2026-2027")) {
            RegistrationPeriod period = new RegistrationPeriod();
            period.setFaculty(faculty);
            period.setSemester(semester);
            period.setName("Đợt KLTN demo 2026-2027");
            period.setProjectType(ProjectType.GRADUATION);
            period.setProposalStartAt(LocalDateTime.of(2026, Month.SEPTEMBER, 15, 0, 0));
            period.setProposalEndAt(LocalDateTime.of(2026, Month.SEPTEMBER, 30, 23, 59));
            period.setRegistrationStartAt(LocalDateTime.of(2026, Month.OCTOBER, 1, 0, 0));
            period.setRegistrationEndAt(LocalDateTime.of(2026, Month.OCTOBER, 15, 23, 59));
            period.setReviewDeadlineAt(LocalDateTime.of(2027, Month.JANUARY, 10, 23, 59));
            period.setDefenseDate(LocalDate.of(2027, Month.JANUARY, 20));
            period.setStatus(RegistrationPeriodStatus.ACTIVE);
            period.setCreatedBy(admin);
            period.setCreatedAt(LocalDateTime.now());
            registrationPeriodRepository.save(period);
        }

        notificationService.publish(
                studentUser.getId(),
                "demo-welcome-student",
                "Chào mừng đến hệ thống",
                "Tài khoản sinh viên demo đã sẵn sàng.",
                "/dashboard");
        notificationService.publish(
                lecturerUser.getId(),
                "demo-welcome-lecturer",
                "Chào mừng đến hệ thống",
                "Tài khoản giảng viên demo đã sẵn sàng.",
                "/dashboard");
    }

    private User ensureUser(String email, String fullName, Set<RoleCode> roleCodes) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(demoPassword));
            user.setFullName(fullName);
            user.setStatus(UserStatus.ACTIVE);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            Set<Role> roles = Set.copyOf(roleRepository.findAllByCodeIn(roleCodes));
            user.replaceRoles(roles, now);
            return userRepository.save(user);
        });
    }
}
