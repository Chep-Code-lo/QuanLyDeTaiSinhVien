package com.example.qldetai.feature.communication.email;

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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "email_notification_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_email_notification_settings_01",
                columnNames = {"event_type", "scope_type", "scope_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EmailNotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EmailNotificationEventType eventType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "sender_name", nullable = false, length = 150)
    private String senderName;

    @Column(name = "from_email", nullable = false, length = 254)
    private String fromEmail;

    @Column(name = "reply_to_email", length = 254)
    private String replyToEmail;

    @Column(name = "subject_template", nullable = false, length = 500)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    /** Comma-separated enum names; values are validated before persistence. */
    @Column(name = "recipient_groups", nullable = false, length = 500)
    private String recipientGroups;

    @Column(name = "reminder_hours")
    private Integer reminderHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private NotificationScopeType scopeType;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;
}
