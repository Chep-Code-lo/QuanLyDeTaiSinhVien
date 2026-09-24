package com.example.qldetai.feature.topic.repository;

import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Page<Topic> findByStatus(TopicStatus status, Pageable pageable);

    Page<Topic> findByPeriodId(Long periodId, Pageable pageable);

    Page<Topic> findByPeriodIdAndStatus(Long periodId, TopicStatus status, Pageable pageable);

    boolean existsByPeriodIdAndCodeIgnoreCase(Long periodId, String code);
}
