package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.StoredFile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f where f.uploadedBy.id = :userId and f.status = com.example.qldetai.feature.progress.domain.StoredFileStatus.READY")
    long readyBytesByUser(Long userId);

    long countByUploadedByIdAndUploadedAtGreaterThanEqual(Long userId, LocalDateTime since);

    @Query("""
            select count(f) from StoredFile f
            where f.uploadedBy.id = :userId
              and f.status = com.example.qldetai.feature.progress.domain.StoredFileStatus.READY
              and not exists (select r.id from ReportSubmission r where r.file = f)
              and not exists (select a.id from Announcement a where a.attachment = f)
            """)
    long countUnattachedByUser(Long userId);
}
