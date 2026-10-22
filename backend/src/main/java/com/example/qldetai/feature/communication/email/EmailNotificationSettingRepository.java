package com.example.qldetai.feature.communication.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailNotificationSettingRepository
        extends JpaRepository<EmailNotificationSetting, Long> {

    Optional<EmailNotificationSetting> findByEventTypeAndScopeTypeAndScopeId(
            EmailNotificationEventType eventType, NotificationScopeType scopeType, Long scopeId);

    List<EmailNotificationSetting> findByScopeTypeAndScopeIdOrderByEventTypeAsc(
            NotificationScopeType scopeType, Long scopeId);
}
