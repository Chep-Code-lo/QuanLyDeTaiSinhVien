package com.example.qldetai.feature.communication.repository;

import com.example.qldetai.feature.communication.domain.Announcement;
import com.example.qldetai.feature.communication.domain.AnnouncementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Page<Announcement> findByStatus(AnnouncementStatus status, Pageable pageable);

    Page<Announcement> findByFacultyId(Long facultyId, Pageable pageable);

    Page<Announcement> findByFacultyIdAndStatus(Long facultyId, AnnouncementStatus status, Pageable pageable);
}
