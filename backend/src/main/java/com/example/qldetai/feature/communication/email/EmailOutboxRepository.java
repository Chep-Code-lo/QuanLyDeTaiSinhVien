package com.example.qldetai.feature.communication.email;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /**
     * Inserts one immutable outbox snapshot and treats only the unique event/recipient
     * collision as an idempotent no-op. This is deliberately native MySQL syntax: the
     * production database is MySQL 8.4, and the test database runs in MySQL mode.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into email_outbox (
                event_type, event_key, recipient_user_id, recipient_email, recipient_name,
                sender_name, from_email, reply_to_email, subject, body, status,
                attempt_count, next_attempt_at, faculty_id, related_period_id,
                related_group_id, related_topic_id, related_assignment_id,
                related_milestone_id, resolved_scope_type, resolved_scope_id,
                created_by, created_at
            ) values (
                :eventType, :eventKey, :recipientUserId, :recipientEmail, :recipientName,
                :senderName, :fromEmail, :replyToEmail, :subject, :body, :status,
                :attemptCount, :nextAttemptAt, :facultyId, :relatedPeriodId,
                :relatedGroupId, :relatedTopicId, :relatedAssignmentId,
                :relatedMilestoneId, :resolvedScopeType, :resolvedScopeId,
                :createdById, :createdAt
            ) on duplicate key update id = id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventType") String eventType,
            @Param("eventKey") String eventKey,
            @Param("recipientUserId") Long recipientUserId,
            @Param("recipientEmail") String recipientEmail,
            @Param("recipientName") String recipientName,
            @Param("senderName") String senderName,
            @Param("fromEmail") String fromEmail,
            @Param("replyToEmail") String replyToEmail,
            @Param("subject") String subject,
            @Param("body") String body,
            @Param("status") String status,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("facultyId") Long facultyId,
            @Param("relatedPeriodId") Long relatedPeriodId,
            @Param("relatedGroupId") Long relatedGroupId,
            @Param("relatedTopicId") Long relatedTopicId,
            @Param("relatedAssignmentId") Long relatedAssignmentId,
            @Param("relatedMilestoneId") Long relatedMilestoneId,
            @Param("resolvedScopeType") String resolvedScopeType,
            @Param("resolvedScopeId") Long resolvedScopeId,
            @Param("createdById") Long createdById,
            @Param("createdAt") LocalDateTime createdAt);

    @EntityGraph(attributePaths = "recipientUser")
    java.util.Optional<EmailOutbox> findByIdAndStatus(Long id, EmailOutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from EmailOutbox o
            where o.status = com.example.qldetai.feature.communication.email.EmailOutboxStatus.PENDING
              and o.nextAttemptAt <= :now
            order by o.createdAt asc
            """)
    List<EmailOutbox> lockReadyForDelivery(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailOutbox> findByStatusAndClaimedAtBefore(
            EmailOutboxStatus status,
            LocalDateTime claimedBefore,
            Pageable pageable);

    Page<EmailOutbox> findByFacultyIdIn(Collection<Long> facultyIds, Pageable pageable);

    /**
     * A faculty-scoped test message can intentionally use sample data and therefore
     * have no business faculty id. Its resolved setting scope is still faculty data
     * and must remain visible to the dean who owns that scope.
     */
    @Query("""
            select o from EmailOutbox o
            where o.facultyId in :facultyIds
               or (o.resolvedScopeType = com.example.qldetai.feature.communication.email.NotificationScopeType.FACULTY
                   and o.resolvedScopeId in :facultyIds)
            """)
    Page<EmailOutbox> findAccessibleToDean(@Param("facultyIds") Collection<Long> facultyIds, Pageable pageable);

    @Query("""
            select o from EmailOutbox o
            where (o.resolvedScopeType = com.example.qldetai.feature.communication.email.NotificationScopeType.LECTURER
                   and o.resolvedScopeId = :userId)
               or o.relatedTopicId in (
                   select ts.topic.id from TopicSupervisor ts where ts.lecturer.user.id = :userId
               )
            """)
    Page<EmailOutbox> findAccessibleToLecturer(@Param("userId") Long userId, Pageable pageable);
}
