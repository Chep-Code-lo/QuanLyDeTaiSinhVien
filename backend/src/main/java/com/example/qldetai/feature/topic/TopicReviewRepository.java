package com.example.qldetai.feature.topic;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicReviewRepository extends JpaRepository<TopicReview, Long> {

    List<TopicReview> findByTopicIdOrderByReviewedAtDesc(Long topicId);
}
