package com.example.qldetai.feature.topic.repository;

import com.example.qldetai.feature.topic.domain.TopicSupervisor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicSupervisorRepository extends JpaRepository<TopicSupervisor, Long> {

    List<TopicSupervisor> findByTopicIdOrderBySlotNoAsc(Long topicId);

    void deleteByTopicId(Long topicId);

    @Query("""
            select count(ts) > 0 from TopicSupervisor ts
            where ts.topic.id = :topicId and ts.lecturer.user.id = :userId
            """)
    boolean existsByTopicIdAndLecturerUserId(@Param("topicId") Long topicId, @Param("userId") Long userId);
}
