package com.example.qldetai.feature.topic.repository;

import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select topic from Topic topic where topic.id = :id")
    Optional<Topic> findByIdForUpdate(Long id);

    @Override
    @EntityGraph(attributePaths = {"period", "faculty", "department", "proposedBy", "reservedGroup"})
    Page<Topic> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"period", "faculty", "department", "proposedBy", "reservedGroup"})
    Page<Topic> findByStatus(TopicStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "faculty", "department", "proposedBy", "reservedGroup"})
    Page<Topic> findByPeriodId(Long periodId, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "faculty", "department", "proposedBy", "reservedGroup"})
    Page<Topic> findByPeriodIdAndStatus(Long periodId, TopicStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "faculty", "department", "proposedBy", "reservedGroup"})
    @Query(
            value = """
                    select topic from Topic topic
                    where (:periodId is null or topic.period.id = :periodId)
                      and (:status is null or topic.status = :status)
                      and (
                        topic.proposedBy.id = :studentUserId
                        or (
                          topic.status = com.example.qldetai.feature.topic.domain.TopicStatus.PUBLISHED
                          and (
                            topic.visibilityType = com.example.qldetai.feature.topic.domain.VisibilityType.OPEN
                            or exists (
                              select member.id from GroupMember member
                              where member.group = topic.reservedGroup
                                and member.student.user.id = :studentUserId
                                and member.leftAt is null
                            )
                          )
                        )
                      )
                    """,
            countQuery = """
                    select count(topic) from Topic topic
                    where (:periodId is null or topic.period.id = :periodId)
                      and (:status is null or topic.status = :status)
                      and (
                        topic.proposedBy.id = :studentUserId
                        or (
                          topic.status = com.example.qldetai.feature.topic.domain.TopicStatus.PUBLISHED
                          and (
                            topic.visibilityType = com.example.qldetai.feature.topic.domain.VisibilityType.OPEN
                            or exists (
                              select member.id from GroupMember member
                              where member.group = topic.reservedGroup
                                and member.student.user.id = :studentUserId
                                and member.leftAt is null
                            )
                          )
                        )
                      )
                    """)
    Page<Topic> findVisibleToStudent(
            @Param("periodId") Long periodId,
            @Param("status") TopicStatus status,
            @Param("studentUserId") Long studentUserId,
            Pageable pageable);

    long countByStatus(TopicStatus status);

    @EntityGraph(attributePaths = "department")
    List<Topic> findTop5ByStatusOrderByUpdatedAtDesc(TopicStatus status);

    @Query("""
            select topic.period.projectType as projectType, count(topic) as count
            from Topic topic
            group by topic.period.projectType
            """)
    List<ProjectTypeCount> countByProjectType();

    boolean existsByPeriodIdAndCodeIgnoreCase(Long periodId, String code);

    interface ProjectTypeCount {
        ProjectType getProjectType();

        long getCount();
    }
}
