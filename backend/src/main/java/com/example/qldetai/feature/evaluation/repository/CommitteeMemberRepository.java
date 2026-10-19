package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.CommitteeMember;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, Long> {

    List<CommitteeMember> findByCommitteeId(Long committeeId);

    boolean existsByCommitteeIdAndLecturerUserIdAndCommitteeRole(
            Long committeeId, Long userId, CommitteeRole committeeRole);
}
