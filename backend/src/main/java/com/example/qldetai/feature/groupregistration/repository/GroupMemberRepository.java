package com.example.qldetai.feature.groupregistration.repository;

import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(Long groupId);

    List<GroupMember> findByStudentIdAndLeftAtIsNullOrderByJoinedAtDesc(Long studentId);

    Optional<GroupMember> findByGroupIdAndStudentIdAndLeftAtIsNull(Long groupId, Long studentId);

    Optional<GroupMember> findByGroupIdAndMemberRoleAndLeftAtIsNull(Long groupId, GroupMemberRole memberRole);

    boolean existsByPeriodIdAndStudentIdAndLeftAtIsNull(Long periodId, Long studentId);

    long countByGroupIdAndLeftAtIsNull(Long groupId);
}
