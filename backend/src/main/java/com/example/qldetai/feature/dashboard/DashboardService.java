package com.example.qldetai.feature.dashboard;

import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.communication.repository.NotificationRepository;
import com.example.qldetai.feature.dashboard.DashboardResponse.Deadline;
import com.example.qldetai.feature.dashboard.DashboardResponse.SemesterSummary;
import com.example.qldetai.feature.dashboard.DashboardResponse.TopicPreview;
import com.example.qldetai.feature.dashboard.DashboardResponse.TopicTypeCount;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final EntityManager entityManager;
    private final NotificationRepository notifications;
    private final Clock clock;

    public DashboardService(EntityManager entityManager, NotificationRepository notifications, Clock clock) {
        this.entityManager = entityManager;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** System-wide totals match the topic and assignment directories; notifications belong to the viewer. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse summary(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        long totalTopics = entityManager.createQuery("select count(t) from Topic t", Long.class).getSingleResult();
        long registeringTopics = entityManager.createQuery("""
                select count(t) from Topic t
                where t.status = :published and t.period.status = :active
                  and t.period.registrationStartAt <= :now and t.period.registrationEndAt >= :now
                  and not exists (select a.id from ProjectAssignment a where a.topic = t)
                """, Long.class)
                .setParameter("published", TopicStatus.PUBLISHED)
                .setParameter("active", RegistrationPeriodStatus.ACTIVE)
                .setParameter("now", now).getSingleResult();

        EnumMap<ProjectType, Long> typeCounts = new EnumMap<>(ProjectType.class);
        entityManager.createQuery("select t.period.projectType, count(t) from Topic t group by t.period.projectType", Object[].class)
                .getResultList().forEach(row -> typeCounts.put((ProjectType) row[0], (Long) row[1]));
        List<TopicTypeCount> topicTypes = Arrays.stream(ProjectType.values())
                .map(type -> new TopicTypeCount(type, typeCounts.getOrDefault(type, 0L))).toList();

        List<TopicPreview> recentTopics = entityManager.createQuery("""
                select t from Topic t join fetch t.department
                order by t.updatedAt desc, t.id desc
                """, Topic.class).setMaxResults(3).getResultList().stream()
                .map(topic -> new TopicPreview(topic.getId(), topic.getCode(), topic.getTitle(),
                        topic.getDepartment().getName(), topic.getStatus())).toList();

        List<RegistrationPeriod> activePeriods = entityManager.createQuery("""
                select p from RegistrationPeriod p
                where p.status = :active and p.registrationEndAt >= :now
                order by p.registrationEndAt, p.id
                """, RegistrationPeriod.class)
                .setParameter("active", RegistrationPeriodStatus.ACTIVE).setParameter("now", now).getResultList();
        List<RegistrationPeriod> openPeriods = activePeriods.stream()
                .filter(period -> !period.getRegistrationStartAt().isAfter(now)).toList();
        List<Deadline> deadlines = new ArrayList<>();
        for (RegistrationPeriod period : activePeriods) {
            if (period.getRegistrationStartAt().isAfter(now)) {
                deadlines.add(new Deadline(period.getId(), period.getName(), "Mở đăng ký đề tài", period.getRegistrationStartAt()));
            }
            deadlines.add(new Deadline(period.getId(), period.getName(), "Kết thúc đăng ký đề tài", period.getRegistrationEndAt()));
        }
        List<Deadline> upcoming = deadlines.stream().sorted(Comparator.comparing(Deadline::dueAt)).limit(4).toList();
        SemesterSummary semester = entityManager.createQuery("""
                select s from Semester s where s.startsOn <= :today and s.endsOn >= :today
                order by s.startsOn desc, s.id desc
                """, Semester.class).setParameter("today", LocalDate.now(clock)).setMaxResults(1).getResultList().stream()
                .map(item -> new SemesterSummary(item.getSemesterNo(), item.getAcademicYear())).findFirst().orElse(null);

        return new DashboardResponse(totalTopics, registeringTopics, countAssignments(AssignmentStatus.IN_PROGRESS),
                countAssignments(AssignmentStatus.COMPLETED), openPeriods.size(),
                notifications.countByUserIdAndReadAtIsNull(userId),
                openPeriods.isEmpty() ? null : openPeriods.getFirst().getRegistrationEndAt(),
                semester, topicTypes, recentTopics, upcoming, now);
    }

    private long countAssignments(AssignmentStatus status) {
        return entityManager.createQuery("select count(distinct a.topic.id) from ProjectAssignment a where a.status = :status", Long.class)
                .setParameter("status", status).getSingleResult();
    }
}
