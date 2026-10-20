package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.DefenseSession;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DefenseSessionRepository
        extends JpaRepository<DefenseSession, Long>, JpaSpecificationExecutor<DefenseSession> {

    java.util.List<DefenseSession> findByCommitteeId(Long committeeId);

    @Query("""
            select count(d) > 0 from DefenseSession d
            where d.committee.id = :committeeId
              and d.status = 'SCHEDULED'
              and d.startsAt < :endsAt
              and d.endsAt > :startsAt
            """)
    boolean hasCommitteeConflict(Long committeeId, LocalDateTime startsAt, LocalDateTime endsAt);
}
