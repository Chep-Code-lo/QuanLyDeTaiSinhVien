package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class EmailRecipientResolverTests {

    @Test
    void resolvesEachNormalizedEmailOnlyOnce() {
        UserRepository userRepository = mock(UserRepository.class);
        EmailRecipientResolver resolver =
                new EmailRecipientResolver(
                        userRepository,
                        mock(StudentRepository.class),
                        mock(LecturerRepository.class),
                        mock(GroupMemberRepository.class),
                        mock(TopicSupervisorRepository.class));
        User first = activeUser(1L, "student@example.test");
        User second = activeUser(2L, "Student@Example.Test");
        when(userRepository.findActiveByIdIn(List.of(1L, 2L))).thenReturn(List.of(first, second));

        List<EmailRecipientResolver.ResolvedRecipient> recipients =
                resolver.resolve(
                        Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS),
                        new EmailEventContext(
                                EmailNotificationEventType.TASK_ASSIGNED,
                                "test",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(1L, 2L),
                                List.of(),
                                Map.of()));

        assertThat(recipients)
                .singleElement()
                .extracting(value -> value.user().getId())
                .isEqualTo(1L);
    }

    private User activeUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName("Recipient " + id);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
