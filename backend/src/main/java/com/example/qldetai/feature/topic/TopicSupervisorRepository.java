package com.example.qldetai.feature.topic;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicSupervisorRepository extends JpaRepository<TopicSupervisor, Long> {

    List<TopicSupervisor> findByTopicIdOrderBySlotNoAsc(Long topicId);

    void deleteByTopicId(Long topicId);
}
