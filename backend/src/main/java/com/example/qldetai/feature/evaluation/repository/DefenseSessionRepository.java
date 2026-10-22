package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.DefenseSession;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DefenseSessionRepository
        extends JpaRepository<DefenseSession, Long>, JpaSpecificationExecutor<DefenseSession> {

    @Override
    @EntityGraph(attributePaths = {"assignment.topic", "assignment.group", "period", "committee"})
    Page<DefenseSession> findAll(Specification<DefenseSession> specification, Pageable pageable);

    Optional<DefenseSession> findByAssignmentId(Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DefenseSession d where d.assignment.id = :assignmentId")
    Optional<DefenseSession> findByAssignmentIdForUpdate(Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select d from DefenseSession d
            where d.status = 'SCHEDULED' and d.startsAt < :endsAt and d.endsAt > :startsAt
              and function('regexp_replace', lower(trim(d.location)), :pattern, '') = :locationKey
            """)
    List<DefenseSession> findRoomConflictsForUpdate(
            String locationKey, String pattern, LocalDateTime startsAt, LocalDateTime endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select d from DefenseSession d
            where d.status = 'SCHEDULED' and d.startsAt < :endsAt and d.endsAt > :startsAt
              and exists (select m.id from CommitteeMember m
                          where m.committee = d.committee and m.lecturer.id in :lecturerIds)
            """)
    List<DefenseSession> findLecturerConflictsForUpdate(
            List<Long> lecturerIds, LocalDateTime startsAt, LocalDateTime endsAt);

    @Query(
            """
            select count(d) > 0 from DefenseSession d
            where d.status = 'SCHEDULED' and d.startsAt < :endsAt and d.endsAt > :startsAt
              and function('regexp_replace', lower(trim(d.location)), :pattern, '') = :locationKey
            """)
    boolean hasRoomConflict(
            String locationKey, String pattern, LocalDateTime startsAt, LocalDateTime endsAt);

    @Query(
            """
            select count(d) > 0 from DefenseSession d
            where d.status = 'SCHEDULED' and d.startsAt < :endsAt and d.endsAt > :startsAt
              and exists (select m.id from CommitteeMember m
                          where m.committee = d.committee and m.lecturer.id in :lecturerIds)
            """)
    boolean hasLecturerConflict(
            List<Long> lecturerIds, LocalDateTime startsAt, LocalDateTime endsAt);

    @EntityGraph(attributePaths = {"assignment.topic", "assignment.group", "period", "committee"})
    @Query(
            """
            select defense from DefenseSession defense
            join GroupMember member on member.group.id = defense.assignment.group.id
            where member.student.user.id = :userId and member.leftAt is null
            order by defense.startsAt desc, defense.id desc
            """)
    List<DefenseSession> findByCurrentStudentUserId(@Param("userId") Long userId);

    @Query(
            """
            select count(d) > 0 from DefenseSession d
            where d.committee.id = :committeeId
              and d.status = 'SCHEDULED'
              and d.startsAt < :endsAt
              and d.endsAt > :startsAt
            """)
    boolean hasCommitteeConflict(Long committeeId, LocalDateTime startsAt, LocalDateTime endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select defense from DefenseSession defense
            where defense.committee.id = :committeeId and defense.status = 'SCHEDULED'
              and defense.startsAt < :endsAt and defense.endsAt > :startsAt
            """)
    List<DefenseSession> findConflictsForUpdate(
            Long committeeId, LocalDateTime startsAt, LocalDateTime endsAt);
}
