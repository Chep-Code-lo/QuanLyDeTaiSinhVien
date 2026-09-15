package com.example.qldetai.feature.academic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.academic.AcademicDtos.ChangePeriodStatusRequest;
import com.example.qldetai.feature.academic.AcademicDtos.CreateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.AcademicDtos.RegistrationPeriodResponse;
import com.example.qldetai.feature.identity.Role;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.RoleRepository;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.identity.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RegistrationPeriodServiceTests {

    @Autowired
    private RegistrationPeriodService service;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
        LocalDateTime now = LocalDateTime.now();
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

        RegistrationPeriodResponse created = service.create(request, admin.getId());

        assertThatThrownBy(() -> service.changeStatus(
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

        assertThatThrownBy(() -> service.create(request, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Required order");
    }
}
