package com.example.qldetai.feature.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in read-only comparison against an existing MySQL database. Never creates test records. */
@SpringBootTest
@EnabledIfSystemProperty(named = "dashboard.mysql.verify", matches = "true")
@Transactional(readOnly = true)
class DashboardMySqlVerificationTests {
    @Autowired DashboardService dashboard;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    void summaryMatchesDirectSqlOnExistingDatabase() {
        long userId = jdbc.queryForObject("select min(id) from users", Long.class);
        var response = dashboard.summary(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        assertThat(response.totalTopics()).isEqualTo(count("select count(*) from topics"));
        assertThat(response.inProgressTopics()).isEqualTo(count("select count(distinct topic_id) from project_assignments where status='IN_PROGRESS'"));
        assertThat(response.completedTopics()).isEqualTo(count("select count(distinct topic_id) from project_assignments where status='COMPLETED'"));
        assertThat(response.registeringTopics()).isEqualTo(jdbc.queryForObject("""
                select count(*) from topics t join registration_periods p on p.id=t.period_id
                where t.status='PUBLISHED' and p.status='ACTIVE'
                  and p.registration_start_at<=? and p.registration_end_at>=?
                  and not exists (select 1 from project_assignments a where a.topic_id=t.id)
                """, Long.class, now, now));
        assertThat(response.openRegistrationPeriods()).isEqualTo(jdbc.queryForObject("""
                select count(*) from registration_periods where status='ACTIVE'
                  and registration_start_at<=? and registration_end_at>=?
                """, Long.class, now, now));
        assertThat(response.unreadNotifications()).isEqualTo(jdbc.queryForObject(
                "select count(*) from notifications where user_id=? and read_at is null", Long.class, userId));
        assertThat(response.topicTypes().stream().mapToLong(DashboardResponse.TopicTypeCount::count).sum()).isEqualTo(response.totalTopics());
        assertThat(response.recentTopics()).extracting(DashboardResponse.TopicPreview::id).containsExactlyElementsOf(
                jdbc.queryForList("select id from topics order by updated_at desc, id desc limit 3", Long.class));
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
