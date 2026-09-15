package com.example.qldetai.feature.topic;

import com.example.qldetai.feature.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "topic_reviews")
@Getter
@Setter
@NoArgsConstructor
public class TopicReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by", nullable = false)
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TopicReviewDecision decision;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
}
