package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.progress.repository.MilestoneRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class EmailNotificationEventServiceTests {

    @Test
    void registrationOpenedDoesNotInterruptTheBusinessActionWhenEmailSettingsAreUnavailable() {
        EmailNotificationSettingsService settingsService = mock(EmailNotificationSettingsService.class);
        EmailRecipientResolver recipientResolver = mock(EmailRecipientResolver.class);
        EmailOutboxService outboxService = mock(EmailOutboxService.class);
        EmailEventContextFactory contextFactory = mock(EmailEventContextFactory.class);
        RegistrationPeriod period = new RegistrationPeriod();
        User actor = new User();
        EmailEventContext context = new EmailEventContext(
                EmailNotificationEventType.REGISTRATION_OPENED,
                "registration-opened:1",
                1L,
                1L,
                null,
                null,
                null,
                null,
                1L,
                List.of(),
                List.of(),
                Map.of());
        when(contextFactory.registrationOpened(period, actor)).thenReturn(context);
        doThrow(new DataAccessResourceFailureException("email settings table is unavailable"))
                .when(settingsService)
                .resolveForEvent(context);

        EmailNotificationEventService service = new EmailNotificationEventService(
                settingsService,
                recipientResolver,
                outboxService,
                contextFactory,
                mock(MilestoneRepository.class),
                new EmailNotificationProperties(),
                Clock.systemUTC());

        assertThatCode(() -> service.registrationOpened(period, actor)).doesNotThrowAnyException();

        verify(settingsService).resolveForEvent(context);
        verifyNoInteractions(recipientResolver, outboxService);
    }
}
