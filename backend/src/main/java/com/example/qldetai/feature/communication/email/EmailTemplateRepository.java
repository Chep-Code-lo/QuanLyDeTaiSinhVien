package com.example.qldetai.feature.communication.email;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    Page<EmailTemplate> findByOwnerId(Long ownerId, Pageable pageable);

    Optional<EmailTemplate> findByIdAndOwnerId(Long id, Long ownerId);
}
