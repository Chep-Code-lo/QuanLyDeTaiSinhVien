package com.example.qldetai.feature.topic.repository;

import com.example.qldetai.feature.topic.domain.TopicSupervisor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TopicSupervisorRepository extends JpaRepository<TopicSupervisor, Long> {

    @EntityGraph(attributePaths = "lecturer.user")
    List<TopicSupervisor> findByTopicIdOrderBySlotNoAsc(Long topicId);

    @EntityGraph(attributePaths = "lecturer.user")
    List<TopicSupervisor> findByTopicIdInOrderBySlotNoAsc(Collection<Long> topicIds);

    void deleteByTopicId(Long topicId);

    @Query(
            """
            select count(ts) > 0 from TopicSupervisor ts
            where ts.topic.id = :topicId and ts.lecturer.user.id = :userId
            """)
    boolean existsByTopicIdAndLecturerUserId(
            @Param("topicId") Long topicId, @Param("userId") Long userId);
}
