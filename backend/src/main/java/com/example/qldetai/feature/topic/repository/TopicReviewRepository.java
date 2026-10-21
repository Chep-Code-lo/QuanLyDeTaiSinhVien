package com.example.qldetai.feature.topic.repository;

import com.example.qldetai.feature.topic.domain.TopicReview;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicReviewRepository extends JpaRepository<TopicReview, Long> {

    List<TopicReview> findByTopicIdOrderByReviewedAtDesc(Long topicId);

    @EntityGraph(attributePaths = "reviewedBy")
    Optional<TopicReview> findFirstByTopicIdOrderByReviewedAtDesc(Long topicId);

    @EntityGraph(attributePaths = "reviewedBy")
    @Query("""
            select review from TopicReview review
            where review.topic.id in :topicIds and not exists (
                select newer.id from TopicReview newer where newer.topic = review.topic
                  and (newer.reviewedAt > review.reviewedAt
                    or (newer.reviewedAt = review.reviewedAt and newer.id > review.id)))
            """)
    List<TopicReview> findLatestByTopicIds(Collection<Long> topicIds);
}
