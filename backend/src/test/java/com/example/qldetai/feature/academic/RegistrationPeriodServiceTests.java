package com.example.qldetai.feature.academic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.dto.AcademicDtos.ChangePeriodStatusRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.CreateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.RegistrationPeriodResponse;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.academic.service.RegistrationPeriodService;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.domain.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class RegistrationPeriodServiceTests {

    private final RegistrationPeriodService registrationPeriodService;
    private final FacultyRepository facultyRepository;
    private final SemesterRepository semesterRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    RegistrationPeriodServiceTests(
            RegistrationPeriodService registrationPeriodService,
            FacultyRepository facultyRepository,
            SemesterRepository semesterRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.registrationPeriodService = registrationPeriodService;
        this.facultyRepository = facultyRepository;
        this.semesterRepository = semesterRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    private Faculty faculty;
    private Semester semester;
    private User admin;

    @BeforeEach
    void setUp() {
        faculty = new Faculty();
        faculty.setCode("F" + System.nanoTime());
        faculty.setName("Test Faculty");
        faculty.setActive(true);
        faculty = facultyRepository.save(faculty);

        semester = new Semester();
        semester.setAcademicYear("2026-2027");
        semester.setSemesterNo(2);
        semester.setStartsOn(LocalDate.of(2027, 2, 1));
        semester.setEndsOn(LocalDate.of(2027, 6, 30));
        semester = semesterRepository.save(semester);

        Role role = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        admin = new User();
        admin.setEmail("period-" + System.nanoTime() + "@test.local");
        admin.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        admin.setFullName("Period Admin");
        admin.setStatus(UserStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        admin.replaceRoles(Set.of(role), now);
        admin = userRepository.save(admin);
    }

    @Test
    void graduationPeriodCannotActivateWithoutRequiredDates() {
        CreateRegistrationPeriodRequest request = new CreateRegistrationPeriodRequest(
                faculty.getId(),
                semester.getId(),
                "Graduation draft",
                ProjectType.GRADUATION,
                LocalDateTime.of(2027, 2, 1, 0, 0),
                LocalDateTime.of(2027, 2, 10, 0, 0),
                LocalDateTime.of(2027, 2, 10, 0, 0),
                LocalDateTime.of(2027, 2, 20, 0, 0),
                null,
                null);

        RegistrationPeriodResponse created = registrationPeriodService.create(request, admin.getId());

        assertThatThrownBy(() -> registrationPeriodService.changeStatus(
                        created.id(), new ChangePeriodStatusRequest(created.version(), RegistrationPeriodStatus.ACTIVE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Review deadline");
    }

    @Test
    void invalidScheduleIsRejected() {
        CreateRegistrationPeriodRequest request = new CreateRegistrationPeriodRequest(
                faculty.getId(),
                semester.getId(),
                "Invalid period",
                ProjectType.COURSE,
                LocalDateTime.of(2027, 2, 10, 0, 0),
                LocalDateTime.of(2027, 2, 1, 0, 0),
                LocalDateTime.of(2027, 2, 12, 0, 0),
                LocalDateTime.of(2027, 2, 20, 0, 0),
                null,
                null);

        assertThatThrownBy(() -> registrationPeriodService.create(request, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Required order");
    }
}
