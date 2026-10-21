package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailNotificationSettingResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.SendTestEmailRequest;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.UpdateNotificationSettingRequest;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.domain.UserStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class EmailNotificationSettingsIntegrationTests {

    private final EmailNotificationSettingsService settingsService;
    private final EmailLogService emailLogService;
    private final EmailOutboxRepository emailOutboxRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final AcademicAppointmentRepository appointmentRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    EmailNotificationSettingsIntegrationTests(
            EmailNotificationSettingsService settingsService,
            EmailLogService emailLogService,
            EmailOutboxRepository emailOutboxRepository,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            LecturerRepository lecturerRepository,
            AcademicAppointmentRepository appointmentRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.settingsService = settingsService;
        this.emailLogService = emailLogService;
        this.emailOutboxRepository = emailOutboxRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.lecturerRepository = lecturerRepository;
        this.appointmentRepository = appointmentRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    private User admin;
    private Faculty facultyOne;
    private Faculty facultyTwo;
    private Department departmentOne;

    @BeforeEach
    void setUp() {
        admin = createUser("email-admin", Set.of(RoleCode.ADMIN));
        facultyOne = createFaculty("FEMAIL1");
        facultyTwo = createFaculty("FEMAIL2");
        departmentOne = createDepartment(facultyOne, "DEMAIL1");
    }

    @Test
    void administratorCanSaveSystemDefault() {
        EmailNotificationSettingResponse saved = settingsService.update(
                admin.getId(),
                EmailNotificationEventType.TASK_ASSIGNED,
                taskRequest(NotificationScopeType.SYSTEM, 0L));

        assertThat(saved.enabled()).isTrue();
        assertThat(saved.scopeType()).isEqualTo(NotificationScopeType.SYSTEM);
        assertThat(saved.scopeId()).isZero();
    }

    @Test
    void resultEmailCannotUseBroadRecipientGroups() {
        var template = EmailTemplateCatalog.definition(EmailNotificationEventType.RESULT_PUBLISHED);
        assertThatThrownBy(() -> settingsService.update(admin.getId(), EmailNotificationEventType.RESULT_PUBLISHED,
                new UpdateNotificationSettingRequest(NotificationScopeType.SYSTEM, 0L, true, "University", "thongbao@test.local",
                        null, template.defaultSubject(), template.defaultBody(), Set.of(EmailRecipientGroup.FACULTY_STUDENTS), null)))
                .isInstanceOf(com.example.qldetai.exception.BusinessRuleException.class);
    }

    @Test
    void studentCannotManageNotificationSettings() {
        User student = createUser("email-student", Set.of(RoleCode.STUDENT));

        assertThatThrownBy(() -> settingsService.update(
                student.getId(), EmailNotificationEventType.TASK_ASSIGNED,
                taskRequest(NotificationScopeType.LECTURER, student.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deanCanManageOnlyOwnFaculty() {
        Lecturer dean = createLecturer("email-dean");
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(dean);
        appointment.setFaculty(facultyOne);
        appointment.setPositionCode(PositionCode.DEAN);
        appointment.setStartsAt(LocalDateTime.now(clock).minusDays(1));
        appointment.setAssignedBy(admin);
        appointmentRepository.save(appointment);

        EmailNotificationSettingResponse saved = settingsService.update(
                dean.getUser().getId(),
                EmailNotificationEventType.REGISTRATION_OPENED,
                registrationOpenedRequest(facultyOne.getId()));
        assertThat(saved.scopeId()).isEqualTo(facultyOne.getId());

        assertThatThrownBy(() -> settingsService.update(
                dean.getUser().getId(),
                EmailNotificationEventType.REGISTRATION_OPENED,
                registrationOpenedRequest(facultyTwo.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deanCanReadOwnFacultyScopedTestEmailFromHistory() {
        Lecturer dean = createLecturer("email-history-dean");
        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(dean);
        appointment.setFaculty(facultyOne);
        appointment.setPositionCode(PositionCode.DEAN);
        appointment.setStartsAt(LocalDateTime.now(clock).minusDays(1));
        appointment.setAssignedBy(admin);
        appointmentRepository.save(appointment);

        settingsService.update(
                dean.getUser().getId(),
                EmailNotificationEventType.REGISTRATION_OPENED,
                registrationOpenedRequest(facultyOne.getId()));
        Long outboxId = settingsService.queueTest(
                dean.getUser().getId(),
                EmailNotificationEventType.REGISTRATION_OPENED,
                new SendTestEmailRequest(NotificationScopeType.FACULTY, facultyOne.getId(), null, null)).outboxId();

        assertThat(emailLogService.list(dean.getUser().getId(), 0, 20).content())
                .extracting(log -> log.id())
                .contains(outboxId);
        assertThat(emailLogService.get(dean.getUser().getId(), outboxId).log().id()).isEqualTo(outboxId);
        assertThat(emailOutboxRepository.findById(outboxId)).isPresent();
    }

    @Test
    void lecturerCanConfigureOnlyTaskAndDeadlineEventsForSelf() {
        Lecturer lecturer = createLecturer("email-lecturer");

        EmailNotificationSettingResponse taskSetting = settingsService.update(
                lecturer.getUser().getId(),
                EmailNotificationEventType.TASK_ASSIGNED,
                taskRequest(NotificationScopeType.LECTURER, lecturer.getUser().getId()));
        assertThat(taskSetting.scopeId()).isEqualTo(lecturer.getUser().getId());

        assertThatThrownBy(() -> settingsService.update(
                lecturer.getUser().getId(),
                EmailNotificationEventType.REGISTRATION_OPENED,
                registrationOpenedRequest(facultyOne.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    private UpdateNotificationSettingRequest taskRequest(NotificationScopeType scopeType, Long scopeId) {
        return new UpdateNotificationSettingRequest(
                scopeType,
                scopeId,
                true,
                "Thông báo đề tài",
                null,
                null,
                "Nhiệm vụ: {{taskName}}",
                "<p>Chào {{studentName}}, {{taskName}}</p>",
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS),
                null);
    }

    private UpdateNotificationSettingRequest registrationOpenedRequest(Long facultyId) {
        return new UpdateNotificationSettingRequest(
                NotificationScopeType.FACULTY,
                facultyId,
                true,
                "Thông báo khoa",
                null,
                null,
                "Đợt {{periodName}} đã mở",
                "<p>Chào {{studentName}}, {{periodName}}</p>",
                Set.of(EmailRecipientGroup.FACULTY_STUDENTS),
                null);
    }

    private Faculty createFaculty(String code) {
        Faculty faculty = new Faculty();
        faculty.setCode(compactCode(code));
        faculty.setName("Khoa " + code);
        faculty.setActive(true);
        return facultyRepository.save(faculty);
    }

    private Department createDepartment(Faculty faculty, String code) {
        Department department = new Department();
        department.setFaculty(faculty);
        department.setCode(compactCode(code));
        department.setName("Bộ môn " + code);
        department.setActive(true);
        return departmentRepository.save(department);
    }

    private Lecturer createLecturer(String prefix) {
        User user = createUser(prefix, Set.of(RoleCode.LECTURER));
        Lecturer lecturer = new Lecturer();
        lecturer.setUser(user);
        lecturer.setDepartment(departmentOne);
        lecturer.setLecturerCode(compactCode(prefix.toUpperCase()));
        lecturer.setAcademicDegree("ThS");
        lecturer.setSpecialization("Kiểm thử");
        return lecturerRepository.save(lecturer);
    }

    private User createUser(String prefix, Set<RoleCode> roleCodes) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setEmail(prefix + "-" + System.nanoTime() + "@test.local");
        user.setFullName(prefix);
        user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        Set<Role> roles = new LinkedHashSet<>(roleRepository.findAllByCodeIn(roleCodes));
        user.replaceRoles(roles, now);
        return userRepository.save(user);
    }

    private String compactCode(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        return (prefix + suffix).substring(0, Math.min(20, prefix.length() + suffix.length()));
    }
}
