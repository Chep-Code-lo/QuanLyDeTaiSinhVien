package com.example.qldetai.feature.groupregistration.repository;

import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TopicRegistrationRepository extends JpaRepository<TopicRegistration, Long> {

    @Override
    @EntityGraph(attributePaths = {"period", "group", "topic", "registeredBy.user", "reviewedBy"})
    Page<TopicRegistration> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"period", "group", "topic", "registeredBy.user", "reviewedBy"})
    Page<TopicRegistration> findByStatus(RegistrationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "group", "topic", "registeredBy.user", "reviewedBy"})
    Page<TopicRegistration> findByPeriodId(Long periodId, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "group", "topic", "registeredBy.user", "reviewedBy"})
    Page<TopicRegistration> findByPeriodIdAndStatus(
            Long periodId, RegistrationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "group", "topic", "registeredBy.user", "reviewedBy"})
    @Query(
            value =
                    """
                    select distinct registration from TopicRegistration registration
                    join GroupMember member on member.group.id = registration.group.id
                    where member.student.user.id = :userId
                      and member.leftAt is null
                      and (:periodId is null or registration.period.id = :periodId)
                      and (:status is null or registration.status = :status)
                    """,
            countQuery =
                    """
                    select count(distinct registration) from TopicRegistration registration
                    join GroupMember member on member.group.id = registration.group.id
                    where member.student.user.id = :userId
                      and member.leftAt is null
                      and (:periodId is null or registration.period.id = :periodId)
                      and (:status is null or registration.status = :status)
                    """)
    Page<TopicRegistration> findByCurrentStudentUserId(
            @Param("userId") Long userId,
            @Param("periodId") Long periodId,
            @Param("status") RegistrationStatus status,
            Pageable pageable);

    boolean existsByGroupIdAndStatusIn(Long groupId, Collection<RegistrationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from TopicRegistration registration where registration.id = :id")
    Optional<TopicRegistration> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select registration from TopicRegistration registration
            where registration.group.id = :groupId and registration.status in :statuses
            """)
    List<TopicRegistration> findByGroupIdAndStatusInForUpdate(
            Long groupId, Collection<RegistrationStatus> statuses);

    boolean existsByGroupIdAndTopicId(Long groupId, Long topicId);

    List<TopicRegistration> findByTopicIdAndStatus(Long topicId, RegistrationStatus status);
}
