-- Upgrade Docker volumes created before email notifications were introduced.
-- Every statement is safe to execute again after the tables have been created.

CREATE TABLE IF NOT EXISTS `email_notification_settings` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(40) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 0,
  `sender_name` VARCHAR(150) NOT NULL,
  `from_email` VARCHAR(254) NOT NULL,
  `reply_to_email` VARCHAR(254) NULL DEFAULT NULL,
  `subject_template` VARCHAR(500) NOT NULL,
  `body_template` MEDIUMTEXT NOT NULL,
  `recipient_groups` VARCHAR(500) NOT NULL,
  `reminder_hours` TINYINT NULL DEFAULT NULL,
  `scope_type` VARCHAR(20) NOT NULL,
  `scope_id` BIGINT NOT NULL COMMENT 'SYSTEM=0; FACULTY=faculties.id; LECTURER=users.id',
  `updated_by` BIGINT NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_email_notification_settings_01` UNIQUE (`event_type`, `scope_type`, `scope_id`),
  CONSTRAINT `ck_email_notification_settings_01` CHECK (`event_type` IN ('TASK_ASSIGNED','TOPIC_APPROVED','TOPIC_REJECTED','DEADLINE_REMINDER','REGISTRATION_OPENED')),
  CONSTRAINT `ck_email_notification_settings_02` CHECK (`enabled` IN (0,1)),
  CONSTRAINT `ck_email_notification_settings_03` CHECK (`scope_type` IN ('SYSTEM','FACULTY','LECTURER')),
  CONSTRAINT `ck_email_notification_settings_04` CHECK ((`scope_type` = 'SYSTEM' AND `scope_id` = 0) OR (`scope_type` <> 'SYSTEM' AND `scope_id` > 0)),
  CONSTRAINT `ck_email_notification_settings_05` CHECK ((`event_type` = 'DEADLINE_REMINDER' AND `reminder_hours` IN (6,12,24,48)) OR (`event_type` <> 'DEADLINE_REMINDER' AND `reminder_hours` IS NULL)),
  CONSTRAINT `ck_email_notification_settings_06` CHECK (`version` >= 0),
  KEY `ix_email_notification_settings_01` (`scope_type`, `scope_id`),
  KEY `ix_email_notification_settings_02` (`updated_by`),
  CONSTRAINT `fk_email_notification_settings_01` FOREIGN KEY (`updated_by`)
    REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='Cấu hình email thông báo theo phạm vi';

CREATE TABLE IF NOT EXISTS `email_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(40) NOT NULL,
  `event_key` VARCHAR(190) COLLATE utf8mb4_0900_bin NOT NULL,
  `recipient_user_id` BIGINT NOT NULL,
  `recipient_email` VARCHAR(254) NOT NULL,
  `recipient_name` VARCHAR(150) NOT NULL,
  `sender_name` VARCHAR(150) NOT NULL,
  `from_email` VARCHAR(254) NOT NULL,
  `reply_to_email` VARCHAR(254) NULL DEFAULT NULL,
  `subject` VARCHAR(500) NOT NULL,
  `body` MEDIUMTEXT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME(6) NOT NULL,
  `claimed_at` DATETIME(6) NULL DEFAULT NULL,
  `sent_at` DATETIME(6) NULL DEFAULT NULL,
  `last_error` VARCHAR(1000) NULL DEFAULT NULL,
  `faculty_id` BIGINT NULL DEFAULT NULL,
  `related_period_id` BIGINT NULL DEFAULT NULL,
  `related_group_id` BIGINT NULL DEFAULT NULL,
  `related_topic_id` BIGINT NULL DEFAULT NULL,
  `related_assignment_id` BIGINT NULL DEFAULT NULL,
  `related_milestone_id` BIGINT NULL DEFAULT NULL,
  `resolved_scope_type` VARCHAR(20) NOT NULL,
  `resolved_scope_id` BIGINT NOT NULL,
  `created_by` BIGINT NULL DEFAULT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_email_outbox_01` UNIQUE (`event_key`, `recipient_user_id`),
  CONSTRAINT `ck_email_outbox_01` CHECK (`event_type` IN ('TASK_ASSIGNED','TOPIC_APPROVED','TOPIC_REJECTED','DEADLINE_REMINDER','REGISTRATION_OPENED')),
  CONSTRAINT `ck_email_outbox_02` CHECK (`status` IN ('PENDING','SENDING','SENT','FAILED')),
  CONSTRAINT `ck_email_outbox_03` CHECK (`attempt_count` >= 0),
  CONSTRAINT `ck_email_outbox_04` CHECK (`resolved_scope_type` IN ('SYSTEM','FACULTY','LECTURER')),
  CONSTRAINT `ck_email_outbox_05` CHECK (`resolved_scope_id` >= 0),
  KEY `ix_email_outbox_01` (`status`, `next_attempt_at`, `created_at`),
  KEY `ix_email_outbox_02` (`faculty_id`, `created_at`),
  KEY `ix_email_outbox_03` (`related_topic_id`, `created_at`),
  KEY `ix_email_outbox_04` (`created_by`),
  KEY `ix_email_outbox_05` (`recipient_user_id`),
  CONSTRAINT `fk_email_outbox_01` FOREIGN KEY (`recipient_user_id`)
    REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_email_outbox_02` FOREIGN KEY (`faculty_id`)
    REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_email_outbox_03` FOREIGN KEY (`created_by`)
    REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_email_outbox_04` FOREIGN KEY (`related_period_id`)
    REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_email_outbox_05` FOREIGN KEY (`related_group_id`)
    REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_email_outbox_06` FOREIGN KEY (`related_topic_id`)
    REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_email_outbox_07` FOREIGN KEY (`related_assignment_id`)
    REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_email_outbox_08` FOREIGN KEY (`related_milestone_id`)
    REFERENCES `milestones` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='Transactional outbox gửi email';

CREATE TABLE IF NOT EXISTS `email_delivery_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `outbox_id` BIGINT NOT NULL,
  `attempt_no` INT NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `error_message` VARCHAR(1000) NULL DEFAULT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_email_delivery_logs_01` CHECK (`attempt_no` >= 0),
  CONSTRAINT `ck_email_delivery_logs_02` CHECK (`status` IN ('PENDING','SENDING','SENT','FAILED')),
  KEY `ix_email_delivery_logs_01` (`outbox_id`, `created_at`),
  CONSTRAINT `fk_email_delivery_logs_01` FOREIGN KEY (`outbox_id`)
    REFERENCES `email_outbox` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='Lịch sử từng lần gửi email';
