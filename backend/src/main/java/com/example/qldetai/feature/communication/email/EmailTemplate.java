package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.persistence.AuditedEntity;
import com.example.qldetai.feature.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Personal reusable templates; automatic notification settings remain separate. */
@Entity
@Table(name = "email_templates")
@Getter
@Setter
@NoArgsConstructor
public class EmailTemplate extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "sender_name", nullable = false, length = 150)
    private String senderName;
    @Column(name = "from_email", nullable = false, length = 254)
    private String fromEmail;
    @Column(name = "reply_to_email", length = 254)
    private String replyToEmail;
    @Column(name = "subject_template", nullable = false, length = 500)
    private String subjectTemplate;
    @Column(name = "body_template", nullable = false, columnDefinition = "mediumtext")
    private String bodyTemplate;
    @Version @Column(nullable = false)
    private int version;
}
