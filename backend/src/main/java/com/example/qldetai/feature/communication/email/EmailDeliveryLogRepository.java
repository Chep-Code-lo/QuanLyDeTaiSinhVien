package com.example.qldetai.feature.communication.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, Long> {

    List<EmailDeliveryLog> findByOutboxIdOrderByCreatedAtAsc(Long outboxId);
}
