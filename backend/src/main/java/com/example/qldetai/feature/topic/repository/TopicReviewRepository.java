package com.example.qldetai.feature.topic.repository;

import com.example.qldetai.feature.topic.domain.TopicReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicReviewRepository extends JpaRepository<TopicReview, Long> {

    List<TopicReview> findByTopicIdOrderByReviewedAtDesc(Long topicId);
}
