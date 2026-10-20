package com.example.qldetai.feature.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.communication.domain.AnnouncementStatus;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.AnnouncementResponse;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.CreateAnnouncementRequest;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.NotificationResponse;
import com.example.qldetai.feature.communication.service.AnnouncementService;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.identity.domain.Role;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.RoleRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AnnouncementNotificationIntegrationTests {

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private Clock clock;

    private Faculty faculty;
    private User student;
    private User lecturer;

    @BeforeEach
    void setUp() {
        faculty = new Faculty();
        faculty.setCode("F" + System.nanoTime());
        faculty.setName("Notification Faculty");
        faculty.setActive(true);
        faculty = facultyRepository.save(faculty);
        student = createUser(UserStatus.ACTIVE, Set.of(RoleCode.STUDENT));
        lecturer = createUser(UserStatus.ACTIVE, Set.of(RoleCode.LECTURER));
    }

    @Test
    void publishingCreatesOneUnreadNotificationPerActiveRecipient() {
        User dualRole = createUser(UserStatus.ACTIVE, Set.of(RoleCode.STUDENT, RoleCode.LECTURER));
        User disabled = createUser(UserStatus.DISABLED, Set.of(RoleCode.STUDENT));
        AnnouncementResponse draft = createAnnouncement("New announcement");
        assertThat(notificationService.unreadCount(student.getId()).unreadCount()).isZero();
        assertThat(notificationService.unreadCount(lecturer.getId()).unreadCount()).isZero();

        announcementService.publish(draft.id());

        for (User recipient : new User[] { student, lecturer, dualRole }) {
            assertThat(notificationService.unreadCount(recipient.getId()).unreadCount()).isEqualTo(
                1
            );
            NotificationResponse notification = notificationService
                .list(recipient.getId(), 0, 20)
                .content()
                .getFirst();
            assertThat(notification.title()).isEqualTo(draft.title());
            assertThat(notification.content()).isEqualTo(draft.content());
            assertThat(notification.targetPath()).isEqualTo("/announcements");
            assertThat(notification.readAt()).isNull();
        }
        assertThat(notificationService.unreadCount(disabled.getId()).unreadCount()).isZero();
        assertThatThrownBy(() -> announcementService.publish(draft.id())).isInstanceOf(
            BusinessRuleException.class
        );
        assertThat(notificationService.unreadCount(student.getId()).unreadCount()).isEqualTo(1);
    }

    @Test
    void aNewPublicationBecomesUnreadAgainAfterThePreviousOneWasViewed() {
        announcementService.publish(createAnnouncement("First announcement").id());
        NotificationResponse first = notificationService
            .list(student.getId(), 0, 20)
            .content()
            .getFirst();
        notificationService.markRead(first.id(), student.getId());
        assertThat(notificationService.unreadCount(student.getId()).unreadCount()).isZero();
        assertThat(notificationService.unreadCount(lecturer.getId()).unreadCount()).isEqualTo(1);

        announcementService.publish(createAnnouncement("Second announcement").id());
        assertThat(notificationService.unreadCount(student.getId()).unreadCount()).isEqualTo(1);
        assertThat(notificationService.unreadCount(lecturer.getId()).unreadCount()).isEqualTo(2);
        for (User recipient : new User[] { student, lecturer }) {
            notificationService
                .list(recipient.getId(), 0, 20)
                .content()
                .forEach(notification ->
                    notificationService.markRead(notification.id(), recipient.getId())
                );
            assertThat(notificationService.unreadCount(recipient.getId()).unreadCount()).isZero();
        }
    }

    @Test
    void archivingADraftRetainsItForManagementWithoutPublishingOrNotifying() {
        AnnouncementResponse draft = createAnnouncement("Draft to archive");

        AnnouncementResponse archived = announcementService.archive(draft.id());

        assertThat(archived.status()).isEqualTo(AnnouncementStatus.ARCHIVED);
        assertThat(archived.publishedAt()).isNull();
        assertThat(announcementService.get(draft.id()).content()).isEqualTo(draft.content());
        assertThat(announcementService.listForManagement(faculty.getId(), AnnouncementStatus.ARCHIVED, 0, 20).content())
                .extracting(AnnouncementResponse::id).containsExactly(draft.id());
        assertThat(announcementService.listPublished(faculty.getId(), 0, 20).content()).isEmpty();
        assertThat(notificationService.unreadCount(student.getId()).unreadCount()).isZero();
        assertThat(announcementService.archive(draft.id()).status()).isEqualTo(AnnouncementStatus.ARCHIVED);
        assertThatThrownBy(() -> announcementService.publish(draft.id())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void archivingAPublicationHidesItFromThePublicFeedAndKeepsItsContent() {
        AnnouncementResponse published = announcementService.publish(createAnnouncement("Published to archive").id());
        assertThat(announcementService.listPublished(faculty.getId(), 0, 20).content())
                .extracting(AnnouncementResponse::id).containsExactly(published.id());

        AnnouncementResponse archived = announcementService.archive(published.id());

        assertThat(archived.status()).isEqualTo(AnnouncementStatus.ARCHIVED);
        assertThat(archived.publishedAt()).isEqualTo(published.publishedAt());
        assertThat(announcementService.get(published.id()).content()).isEqualTo(published.content());
        assertThat(announcementService.listPublished(faculty.getId(), 0, 20).content()).isEmpty();
        assertThat(announcementService.listForManagement(faculty.getId(), AnnouncementStatus.ARCHIVED, 0, 20).content())
                .extracting(AnnouncementResponse::id).containsExactly(published.id());
        assertThat(notificationService.unreadCount(student.getId()).unreadCount()).isEqualTo(1);
    }

    private AnnouncementResponse createAnnouncement(String title) {
        return announcementService.create(
            new CreateAnnouncementRequest(faculty.getId(), title, "Announcement content", null),
            lecturer.getId()
        );
    }

    private User createUser(UserStatus status, Set<RoleCode> roleCodes) {
        Set<Role> roles = roleCodes
            .stream()
            .map(code -> roleRepository.findByCode(code).orElseThrow())
            .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setEmail("announcement-" + System.nanoTime() + "@test.local");
        user.setPasswordHash("unused-in-notification-test");
        user.setFullName("Announcement recipient");
        user.setStatus(status);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.replaceRoles(roles, now);
        return userRepository.save(user);
    }
}
