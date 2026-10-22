package com.example.qldetai.feature.communication.repository;

import com.example.qldetai.feature.communication.domain.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    @org.springframework.data.jpa.repository.Query(
            """
            select distinct n from Notification n, Announcement a
            where n.user.id = :userId and a.id in :ids
              and a.status = com.example.qldetai.feature.communication.domain.AnnouncementStatus.PUBLISHED
              and n.targetPath = '/announcements' and n.title = a.title and n.content = a.content
            order by n.id desc
            """)
    java.util.List<Notification> findForAnnouncements(Long userId, java.util.Collection<Long> ids);

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    boolean existsByUserIdAndEventKey(Long userId, String eventKey);
}
