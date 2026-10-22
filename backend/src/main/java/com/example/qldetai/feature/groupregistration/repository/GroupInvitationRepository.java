package com.example.qldetai.feature.groupregistration.repository;

import com.example.qldetai.feature.groupregistration.domain.GroupInvitation;
import com.example.qldetai.feature.groupregistration.domain.GroupInvitationStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    Page<GroupInvitation> findByInvitedStudentIdOrderByCreatedAtDesc(
            Long studentId, Pageable pageable);

    Page<GroupInvitation> findByInvitedStudentIdAndStatusOrderByCreatedAtDesc(
            Long studentId, GroupInvitationStatus status, Pageable pageable);

    List<GroupInvitation> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    boolean existsByGroupIdAndInvitedStudentIdAndStatus(
            Long groupId, Long invitedStudentId, GroupInvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from GroupInvitation invitation where invitation.id = :id")
    Optional<GroupInvitation> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            update GroupInvitation invitation
               set invitation.status = com.example.qldetai.feature.groupregistration.domain.GroupInvitationStatus.EXPIRED,
                   invitation.respondedAt = :now
             where invitation.status = com.example.qldetai.feature.groupregistration.domain.GroupInvitationStatus.PENDING
               and invitation.expiresAt <= :now
            """)
    int expirePendingAtOrBefore(@Param("now") LocalDateTime now);
}
