package com.example.qldetai.feature.communication.email;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, Long> {

    List<EmailDeliveryLog> findByOutboxIdOrderByCreatedAtAsc(Long outboxId);
}
