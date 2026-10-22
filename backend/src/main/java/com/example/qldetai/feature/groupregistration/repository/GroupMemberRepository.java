package com.example.qldetai.feature.groupregistration.repository;

import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    @EntityGraph(attributePaths = "student.user")
    List<GroupMember> findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(Long groupId);

    @EntityGraph(attributePaths = "student.user")
    List<GroupMember> findByGroupIdInAndLeftAtIsNullOrderBySlotNoAsc(Collection<Long> groupIds);

    List<GroupMember> findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(Long studentId);

    Optional<GroupMember> findByGroupIdAndStudentIdAndLeftAtIsNull(Long groupId, Long studentId);

    boolean existsByGroupIdAndStudentIdAndLeftAtIsNull(Long groupId, Long studentId);

    boolean existsByGroupIdAndStudentUserIdAndLeftAtIsNull(Long groupId, Long userId);

    Optional<GroupMember> findByGroupIdAndMemberRoleAndLeftAtIsNull(
            Long groupId, GroupMemberRole memberRole);

    boolean existsByPeriodIdAndStudentIdAndLeftAtIsNull(Long periodId, Long studentId);

    // Lock both joined tables. Hibernate's "FOR UPDATE OF m" otherwise leaves a newly
    // created group invisible in the join under MySQL REPEATABLE READ.
    @Query(
            value =
                    """
            select m.* from group_members m join student_groups g on g.id = m.group_id
            where m.student_id = :studentId and m.left_at is null
              and m.period_id = :periodId
            for update
            """,
            nativeQuery = true)
    List<GroupMember> findConflictingMemberships(Long periodId, Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select member from GroupMember member where member.group.id = :groupId
              and member.leftAt is null order by member.slotNo
            """)
    List<GroupMember> findActiveByGroupIdForUpdate(Long groupId);

    long countByGroupIdAndLeftAtIsNull(Long groupId);
}
