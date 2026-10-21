package com.example.qldetai.feature.dashboard.service;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.communication.repository.NotificationRepository;
import com.example.qldetai.feature.dashboard.dto.DashboardDtos.CurrentSemesterResponse;
import com.example.qldetai.feature.dashboard.dto.DashboardDtos.DashboardSummaryResponse;
import com.example.qldetai.feature.dashboard.dto.DashboardDtos.RecentTopicResponse;
import com.example.qldetai.feature.dashboard.dto.DashboardDtos.TopicTypeCountResponse;
import com.example.qldetai.feature.dashboard.dto.DashboardDtos.UpcomingDeadlineResponse;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TopicRepository topicRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final SemesterRepository semesterRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;


    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<RegistrationPeriod> activePeriods = registrationPeriodRepository
                .findByStatusOrderByRegistrationEndAtAsc(RegistrationPeriodStatus.ACTIVE);

        return new DashboardSummaryResponse(
                topicRepository.count(),
                topicRepository.countByStatus(TopicStatus.PUBLISHED),
                projectAssignmentRepository.countByStatus(AssignmentStatus.IN_PROGRESS),
                projectAssignmentRepository.countByStatus(AssignmentStatus.COMPLETED),
                activePeriods.size(),
                notificationRepository.countByUserIdAndReadAtIsNull(userId),
                activePeriods.stream()
                        .map(RegistrationPeriod::getRegistrationEndAt)
                        .filter(deadline -> deadline.isAfter(now))
                        .min(Comparator.naturalOrder())
                        .orElse(null),
                currentSemester(LocalDate.now(clock)),
                topicRepository.countByProjectType().stream()
                        .map(count -> new TopicTypeCountResponse(count.getProjectType(), count.getCount()))
                        .toList(),
                topicRepository.findTop5ByStatusOrderByUpdatedAtDesc(TopicStatus.PUBLISHED).stream()
                        .map(topic -> new RecentTopicResponse(
                                topic.getId(),
                                topic.getCode(),
                                topic.getTitle(),
                                topic.getDepartment().getName(),
                                topic.getStatus().name()))
                        .toList(),
                upcomingDeadlines(activePeriods, now),
                now);
    }

    private CurrentSemesterResponse currentSemester(LocalDate today) {
        return semesterRepository
                .findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnDesc(today, today)
                .or(() -> semesterRepository.findFirstByStartsOnLessThanEqualOrderByStartsOnDesc(today))
                .map(semester -> new CurrentSemesterResponse(semester.getSemesterNo(), semester.getAcademicYear()))
                .orElse(null);
    }

    private List<UpcomingDeadlineResponse> upcomingDeadlines(
            List<RegistrationPeriod> periods,
            LocalDateTime now) {
        return periods.stream()
                .flatMap(period -> Stream.of(
                        deadline(period, "Kết thúc đăng ký giảng viên", period.getProposalEndAt()),
                        deadline(period, "Kết thúc đăng ký sinh viên", period.getRegistrationEndAt()),
                        deadline(period, "Hạn GVPB nộp điểm", period.getReviewDeadlineAt()),
                        deadline(
                                period,
                                "Báo cáo hội đồng",
                                period.getDefenseDate() == null ? null : period.getDefenseDate().atStartOfDay())))
                .flatMap(java.util.Optional::stream)
                .filter(deadline -> deadline.dueAt().isAfter(now))
                .sorted(Comparator.comparing(UpcomingDeadlineResponse::dueAt))
                .limit(5)
                .toList();
    }

    private java.util.Optional<UpcomingDeadlineResponse> deadline(
            RegistrationPeriod period,
            String title,
            LocalDateTime dueAt) {
        if (dueAt == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new UpcomingDeadlineResponse(period.getId(), period.getName(), title, dueAt));
    }
}
