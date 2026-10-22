package com.example.qldetai.feature.communication.repository;

import com.example.qldetai.feature.communication.domain.Announcement;
import com.example.qldetai.feature.communication.domain.AnnouncementStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AnnouncementRepository
        extends JpaRepository<Announcement, Long>, JpaSpecificationExecutor<Announcement> {

    @Override
    @EntityGraph(attributePaths = {"faculty", "createdBy", "attachment"})
    Page<Announcement> findAll(Specification<Announcement> specification, Pageable pageable);

    Page<Announcement> findByStatus(AnnouncementStatus status, Pageable pageable);

    Page<Announcement> findByFacultyId(Long facultyId, Pageable pageable);

    Page<Announcement> findByFacultyIdAndStatus(
            Long facultyId, AnnouncementStatus status, Pageable pageable);

    List<Announcement> findByAttachmentId(Long attachmentId);
}
