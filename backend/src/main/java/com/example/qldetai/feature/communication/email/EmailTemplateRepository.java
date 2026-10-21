package com.example.qldetai.feature.communication.email;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    Page<EmailTemplate> findByOwnerId(Long ownerId, Pageable pageable);
    Optional<EmailTemplate> findByIdAndOwnerId(Long id, Long ownerId);
}
