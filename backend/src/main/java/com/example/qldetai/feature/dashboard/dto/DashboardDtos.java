package com.example.qldetai.feature.dashboard.dto;

import com.example.qldetai.feature.academic.domain.ProjectType;

import java.time.LocalDateTime;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record DashboardSummaryResponse(
            long totalTopics,
            long registeringTopics,
            long inProgressTopics,
            long completedTopics,
            long openRegistrationPeriods,
            long unreadNotifications,
            LocalDateTime nextRegistrationCloseAt,
            CurrentSemesterResponse currentSemester,
            List<TopicTypeCountResponse> topicTypes,
            List<RecentTopicResponse> recentTopics,
            List<UpcomingDeadlineResponse> upcomingDeadlines,
            LocalDateTime updatedAt) {}

    public record CurrentSemesterResponse(int semesterNo, String academicYear) {}

    public record TopicTypeCountResponse(ProjectType projectType, long count) {}

    public record RecentTopicResponse(
            Long id, String code, String title, String departmentName, String status) {}

    public record UpcomingDeadlineResponse(
            Long periodId, String periodName, String title, LocalDateTime dueAt) {}
}
