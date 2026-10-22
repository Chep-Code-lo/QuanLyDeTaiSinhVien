package com.example.qldetai.feature.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.dto.AcademicDtos.ChangePeriodStatusRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.CreateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.RegistrationPeriodResponse;
import com.example.qldetai.feature.academic.dto.AcademicDtos.UpdateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.academic.service.RegistrationPeriodService;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.UpdateNotificationSettingRequest;
import com.example.qldetai.feature.communication.email.EmailNotificationEventType;
import com.example.qldetai.feature.communication.email.EmailNotificationSettingsService;
import com.example.qldetai.feature.communication.email.EmailOutboxRepository;
import com.example.qldetai.feature.communication.email.EmailRecipientGroup;
import com.example.qldetai.feature.communication.email.NotificationScopeType;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class RegistrationPeriodServiceTests {

    private final RegistrationPeriodService registrationPeriodService;
    private final FacultyRepository facultyRepository;
    private final SemesterRepository semesterRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailNotificationSettingsService emailNotificationSettingsService;
    private final EmailOutboxRepository emailOutboxRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    RegistrationPeriodServiceTests(
            RegistrationPeriodService registrationPeriodService,
            FacultyRepository facultyRepository,
            SemesterRepository semesterRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailNotificationSettingsService emailNotificationSettingsService,
            EmailOutboxRepository emailOutboxRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.registrationPeriodService = registrationPeriodService;
        this.facultyRepository = facultyRepository;
        this.semesterRepository = semesterRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailNotificationSettingsService = emailNotificationSettingsService;
        this.emailOutboxRepository = emailOutboxRepository;
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
        CreateRegistrationPeriodRequest request =
                new CreateRegistrationPeriodRequest(
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

        RegistrationPeriodResponse created =
                registrationPeriodService.create(request, admin.getId());

        assertThatThrownBy(
                        () ->
                                registrationPeriodService.changeStatus(
                                        created.id(),
                                        new ChangePeriodStatusRequest(
                                                created.version(),
                                                RegistrationPeriodStatus.ACTIVE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Review deadline");
    }

    @Test
    void invalidScheduleIsRejected() {
        CreateRegistrationPeriodRequest request =
                new CreateRegistrationPeriodRequest(
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

    @ParameterizedTest
    @CsvSource({
        "2027-04-30T16:59:00,true",
        "2027-04-30T17:00:00,true",
        "2027-05-01T16:59:00,true",
        "2027-05-01T17:00:00,false",
        "2027-05-05T01:00:00,false"
    })
    void reviewDeadlineCannotFallAfterVietnameseDefenseDay(LocalDateTime deadline, boolean valid) {
        var request = graduationPeriod(deadline);
        if (valid) {
            var created = registrationPeriodService.create(request, admin.getId());
            assertThat(created.reviewDeadlineAt()).isEqualTo(deadline);
            assertThat(created.defenseDate()).isEqualTo(LocalDate.of(2027, 5, 1));
            assertThat(registrationPeriodService.get(created.id()).defenseDate())
                    .isEqualTo(created.defenseDate());
        } else {
            assertThatThrownBy(() -> registrationPeriodService.create(request, admin.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Hạn phản biện không được sau ngày bảo vệ KLTN theo giờ Việt Nam.");
        }
    }

    @Test
    void updatingPeriodWithReviewAfterDefenseIsRejectedWithoutChangingItsDates() {
        var request = graduationPeriod(LocalDateTime.of(2027, 4, 30, 10, 0));
        var created = registrationPeriodService.create(request, admin.getId());
        var update =
                new UpdateRegistrationPeriodRequest(
                        created.version(),
                        semester.getId(),
                        created.name(),
                        ProjectType.GRADUATION,
                        request.proposalStartAt(),
                        request.proposalEndAt(),
                        request.registrationStartAt(),
                        request.registrationEndAt(),
                        LocalDateTime.of(2027, 5, 5, 1, 0),
                        request.defenseDate());
        assertThatThrownBy(() -> registrationPeriodService.update(created.id(), update))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Hạn phản biện");
        assertThat(registrationPeriodService.get(created.id()).reviewDeadlineAt())
                .isEqualTo(request.reviewDeadlineAt());
        assertThat(registrationPeriodService.get(created.id()).defenseDate())
                .isEqualTo(request.defenseDate());
    }

    private CreateRegistrationPeriodRequest graduationPeriod(LocalDateTime deadline) {
        return new CreateRegistrationPeriodRequest(
                faculty.getId(),
                semester.getId(),
                "Graduation dates",
                ProjectType.GRADUATION,
                LocalDateTime.of(2027, 2, 1, 0, 0),
                LocalDateTime.of(2027, 2, 10, 0, 0),
                LocalDateTime.of(2027, 2, 10, 0, 0),
                LocalDateTime.of(2027, 2, 20, 0, 0),
                deadline,
                LocalDate.of(2027, 5, 1));
    }

    @Test
    void committeeRulesRejectCountsOutsideThreeToFive() {
        for (int[] limits : new int[][] {{2, 5}, {3, 6}, {5, 3}}) {
            var request = committeePeriod(limits[0], limits[1]);
            assertThatThrownBy(() -> registrationPeriodService.create(request, admin.getId()))
                    .isInstanceOf(BusinessRuleException.class);
        }
        assertThat(
                        registrationPeriodService
                                .create(committeePeriod(3, 5), admin.getId())
                                .maxCommitteeMembers())
                .isEqualTo(5);
        assertThat(
                        registrationPeriodService
                                .create(committeePeriod(5, 5), admin.getId())
                                .minCommitteeMembers())
                .isEqualTo(5);
    }

    private CreateRegistrationPeriodRequest committeePeriod(int minimum, int maximum) {
        return new CreateRegistrationPeriodRequest(
                faculty.getId(),
                semester.getId(),
                "Committee limits",
                ProjectType.COURSE,
                LocalDateTime.of(2027, 2, 1, 0, 0),
                LocalDateTime.of(2027, 2, 10, 0, 0),
                LocalDateTime.of(2027, 2, 10, 0, 0),
                LocalDateTime.of(2027, 2, 20, 0, 0),
                null,
                null,
                1,
                3,
                72,
                minimum,
                maximum);
    }

    @Test
    void registrationOpenedQueuesExactlyOneAdminEmailWhenDraftBecomesActive() {
        emailNotificationSettingsService.update(
                admin.getId(),
                EmailNotificationEventType.REGISTRATION_OPENED,
                new UpdateNotificationSettingRequest(
                        NotificationScopeType.SYSTEM,
                        0L,
                        true,
                        "Thông báo hệ thống",
                        null,
                        null,
                        "Đợt {{periodName}} đã mở",
                        "<p>Chào {{studentName}}, {{periodName}}</p>",
                        Set.of(EmailRecipientGroup.ADMINS),
                        null));

        RegistrationPeriodResponse created =
                registrationPeriodService.create(
                        new CreateRegistrationPeriodRequest(
                                faculty.getId(),
                                semester.getId(),
                                "Course draft",
                                ProjectType.COURSE,
                                LocalDateTime.of(2027, 2, 1, 0, 0),
                                LocalDateTime.of(2027, 2, 10, 0, 0),
                                LocalDateTime.of(2027, 2, 10, 0, 0),
                                LocalDateTime.of(2027, 2, 20, 0, 0),
                                null,
                                null),
                        admin.getId());

        RegistrationPeriodResponse activated =
                registrationPeriodService.changeStatus(
                        created.id(),
                        new ChangePeriodStatusRequest(
                                created.version(), RegistrationPeriodStatus.ACTIVE),
                        admin.getId());

        assertThat(activated.status()).isEqualTo(RegistrationPeriodStatus.ACTIVE);
        assertThat(emailOutboxRepository.findAll())
                .singleElement()
                .satisfies(
                        outbox -> {
                            assertThat(outbox.getEventType())
                                    .isEqualTo(EmailNotificationEventType.REGISTRATION_OPENED);
                            assertThat(outbox.getRecipientUser().getId()).isEqualTo(admin.getId());
                        });
    }

    @Test
    void closedPeriodCanBeReactivated() {
        RegistrationPeriodResponse created =
                registrationPeriodService.create(
                        new CreateRegistrationPeriodRequest(
                                faculty.getId(),
                                semester.getId(),
                                "Course period",
                                ProjectType.COURSE,
                                LocalDateTime.of(2027, 2, 1, 0, 0),
                                LocalDateTime.of(2027, 2, 10, 0, 0),
                                LocalDateTime.of(2027, 2, 10, 0, 0),
                                LocalDateTime.of(2027, 2, 20, 0, 0),
                                null,
                                null),
                        admin.getId());

        RegistrationPeriodResponse active =
                registrationPeriodService.changeStatus(
                        created.id(),
                        new ChangePeriodStatusRequest(
                                created.version(), RegistrationPeriodStatus.ACTIVE),
                        admin.getId());
        RegistrationPeriodResponse closed =
                registrationPeriodService.changeStatus(
                        active.id(),
                        new ChangePeriodStatusRequest(
                                active.version(), RegistrationPeriodStatus.CLOSED),
                        admin.getId());
        RegistrationPeriodResponse reactivated =
                registrationPeriodService.changeStatus(
                        closed.id(),
                        new ChangePeriodStatusRequest(
                                closed.version(), RegistrationPeriodStatus.ACTIVE),
                        admin.getId());

        assertThat(active.status()).isEqualTo(RegistrationPeriodStatus.ACTIVE);
        assertThat(closed.status()).isEqualTo(RegistrationPeriodStatus.CLOSED);
        assertThat(reactivated.status()).isEqualTo(RegistrationPeriodStatus.ACTIVE);
        assertThat(reactivated.version()).isGreaterThan(closed.version());
    }
}
