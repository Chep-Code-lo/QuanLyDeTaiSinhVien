package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.persistence.CreatedAtEntity;
import com.example.qldetai.feature.identity.domain.User;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "email_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_email_outbox_01",
                columnNames = {"event_key", "recipient_user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EmailNotificationEventType eventType;

    @Column(name = "event_key", nullable = false, length = 190)
    private String eventKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "sender_name", nullable = false, length = 150)
    private String senderName;

    @Column(name = "from_email", nullable = false, length = 254)
    private String fromEmail;

    @Column(name = "reply_to_email", length = 254)
    private String replyToEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "faculty_id")
    private Long facultyId;

    @Column(name = "related_period_id")
    private Long relatedPeriodId;

    @Column(name = "related_group_id")
    private Long relatedGroupId;

    @Column(name = "related_topic_id")
    private Long relatedTopicId;

    @Column(name = "related_assignment_id")
    private Long relatedAssignmentId;

    @Column(name = "related_milestone_id")
    private Long relatedMilestoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_scope_type", nullable = false, length = 20)
    private NotificationScopeType resolvedScopeType;

    @Column(name = "resolved_scope_id", nullable = false)
    private Long resolvedScopeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

}
