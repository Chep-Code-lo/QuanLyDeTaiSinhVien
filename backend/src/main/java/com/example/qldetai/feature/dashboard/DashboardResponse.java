package com.example.qldetai.feature.dashboard;

import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        long totalTopics,
        long registeringTopics,
        long inProgressTopics,
        long completedTopics,
        long openRegistrationPeriods,
        long unreadNotifications,
        LocalDateTime nextRegistrationCloseAt,
        SemesterSummary currentSemester,
        List<TopicTypeCount> topicTypes,
        List<TopicPreview> recentTopics,
        List<Deadline> upcomingDeadlines,
        LocalDateTime updatedAt) {

    public record SemesterSummary(int semesterNo, String academicYear) {}

    public record TopicTypeCount(ProjectType projectType, long count) {}

    public record TopicPreview(Long id, String code, String title, String departmentName, TopicStatus status) {}

    public record Deadline(Long periodId, String periodName, String title, LocalDateTime dueAt) {}
}
