package com.example.qldetai.feature.groupregistration.repository;

import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(Long groupId);

    List<GroupMember> findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(Long studentId);

    Optional<GroupMember> findByGroupIdAndStudentIdAndLeftAtIsNull(Long groupId, Long studentId);

    boolean existsByGroupIdAndStudentIdAndLeftAtIsNull(Long groupId, Long studentId);

    boolean existsByGroupIdAndStudentUserIdAndLeftAtIsNull(Long groupId, Long userId);

    Optional<GroupMember> findByGroupIdAndMemberRoleAndLeftAtIsNull(Long groupId, GroupMemberRole memberRole);

    boolean existsByPeriodIdAndStudentIdAndLeftAtIsNull(Long periodId, Long studentId);

    // Use a current locking read after locking the student, including under MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from GroupMember m
            where m.student.id = :studentId and m.leftAt is null
              and (m.period.id = :periodId or
                m.group.status <> com.example.qldetai.feature.groupregistration.domain.GroupStatus.CLOSED)
            """)
    List<GroupMember> findConflictingMemberships(Long periodId, Long studentId);

    long countByGroupIdAndLeftAtIsNull(Long groupId);
}
