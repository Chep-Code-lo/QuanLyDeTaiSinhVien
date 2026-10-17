package com.example.qldetai.feature.communication.email;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailNotificationSettingRepository extends JpaRepository<EmailNotificationSetting, Long> {

    Optional<EmailNotificationSetting> findByEventTypeAndScopeTypeAndScopeId(
            EmailNotificationEventType eventType,
            NotificationScopeType scopeType,
            Long scopeId);

    List<EmailNotificationSetting> findByScopeTypeAndScopeIdOrderByEventTypeAsc(
            NotificationScopeType scopeType,
            Long scopeId);
}
