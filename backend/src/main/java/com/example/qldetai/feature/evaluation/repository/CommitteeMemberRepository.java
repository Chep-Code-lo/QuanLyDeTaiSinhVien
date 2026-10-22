package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.CommitteeMember;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, Long> {

    @EntityGraph(attributePaths = "lecturer.user")
    List<CommitteeMember> findByCommitteeId(Long committeeId);

    @EntityGraph(attributePaths = "lecturer.user")
    List<CommitteeMember> findByCommitteeIdIn(Collection<Long> committeeIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from CommitteeMember member where member.committee.id = :committeeId")
    List<CommitteeMember> findByCommitteeIdForUpdate(Long committeeId);

    boolean existsByCommitteeIdAndLecturerUserIdAndCommitteeRole(
            Long committeeId, Long userId, CommitteeRole committeeRole);
}
