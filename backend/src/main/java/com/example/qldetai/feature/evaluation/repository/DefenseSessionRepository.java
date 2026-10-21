package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.DefenseSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DefenseSessionRepository
        extends JpaRepository<DefenseSession, Long>, JpaSpecificationExecutor<DefenseSession> {

    @Override
    @EntityGraph(attributePaths = {"assignment.topic", "assignment.group", "period", "committee"})
    Page<DefenseSession> findAll(Specification<DefenseSession> specification, Pageable pageable);

    Optional<DefenseSession> findByAssignmentId(Long assignmentId);

    @Query("""
            select defense from DefenseSession defense
            join GroupMember member on member.group.id = defense.assignment.group.id
            where member.student.user.id = :userId and member.leftAt is null
            order by defense.startsAt desc, defense.id desc
            """)
    List<DefenseSession> findByCurrentStudentUserId(@Param("userId") Long userId);

    @Query("""
            select count(d) > 0 from DefenseSession d
            where d.committee.id = :committeeId
              and d.status = 'SCHEDULED'
              and d.startsAt < :endsAt
              and d.endsAt > :startsAt
            """)
    boolean hasCommitteeConflict(Long committeeId, LocalDateTime startsAt, LocalDateTime endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select defense from DefenseSession defense
            where defense.committee.id = :committeeId and defense.status = 'SCHEDULED'
              and defense.startsAt < :endsAt and defense.endsAt > :startsAt
            """)
    List<DefenseSession> findConflictsForUpdate(Long committeeId, LocalDateTime startsAt, LocalDateTime endsAt);
}
