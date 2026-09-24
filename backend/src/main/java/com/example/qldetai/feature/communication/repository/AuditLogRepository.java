package com.example.qldetai.feature.communication.repository;

import com.example.qldetai.feature.communication.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeIgnoreCase(String entityType, Pageable pageable);
}
