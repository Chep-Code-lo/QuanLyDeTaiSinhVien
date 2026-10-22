package com.example.qldetai.feature.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.communication.repository.NotificationRepository;
import com.example.qldetai.feature.dashboard.service.DashboardService;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

class DashboardServiceTests {
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T05:00:00Z"), ZoneOffset.UTC);
    private final DashboardService service =
            new DashboardService(
                    mock(TopicRepository.class),
                    mock(ProjectAssignmentRepository.class),
                    mock(RegistrationPeriodRepository.class),
                    semesters,
                    mock(NotificationRepository.class),
                    clock);
    private final LocalDate today = LocalDate.now(clock);

    @Test
    void choosesCurrentSemesterWithoutLoadingCatalogOrCallingFallback() {
        when(semesters.findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnDesc(
                        today, today))
                .thenReturn(Optional.of(semester()));
        assertThat(service.summary(1L).currentSemester().academicYear()).isEqualTo("2026-2027");
        verify(semesters, never()).findFirstByStartsOnLessThanEqualOrderByStartsOnDesc(any());
        verify(semesters, never()).findAll();
    }

    @Test
    void fallsBackToMostRecentlyStartedSemester() {
        when(semesters.findFirstByStartsOnLessThanEqualOrderByStartsOnDesc(today))
                .thenReturn(Optional.of(semester()));
        assertThat(service.summary(1L).currentSemester().semesterNo()).isEqualTo(1);
        verify(semesters, never()).findAll();
    }

    @Test
    void returnsNullWhenNoSemesterHasStarted() {
        assertThat(service.summary(1L).currentSemester()).isNull();
    }

    private Semester semester() {
        Semester semester = new Semester();
        semester.setAcademicYear("2026-2027");
        semester.setSemesterNo(1);
        return semester;
    }
}
