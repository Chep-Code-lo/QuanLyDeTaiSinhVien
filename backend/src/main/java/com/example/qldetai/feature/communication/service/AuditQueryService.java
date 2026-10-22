package com.example.qldetai.feature.communication.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.communication.domain.AuditLog;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.AuditLogResponse;
import com.example.qldetai.feature.communication.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(String entityType, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLogResponse> logs =
                (entityType == null || entityType.isBlank()
                                ? auditLogRepository.findAll(pageable)
                                : auditLogRepository.findByEntityTypeIgnoreCase(
                                        entityType.trim(), pageable))
                        .map(this::toResponse);
        return PageResponse.from(logs);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActor() == null ? null : log.getActor().getId(),
                log.getActor() == null ? null : log.getActor().getFullName(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOutcome(),
                log.getRequestId(),
                log.getDurationMs(),
                log.getCreatedAt());
    }
}
