-- Allow result-publication email events without changing existing rows.
-- Each ALTER replaces its CHECK atomically; re-running this file is safe.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

SET @result_email_sql = IF(EXISTS (
  SELECT 1 FROM information_schema.check_constraints
  WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_email_notification_settings_01'
    AND check_clause LIKE '%RESULT_PUBLISHED%'
), 'SELECT 1', 'ALTER TABLE `email_notification_settings` DROP CHECK `ck_email_notification_settings_01`, ADD CONSTRAINT `ck_email_notification_settings_01` CHECK (`event_type` IN (''TASK_ASSIGNED'',''TOPIC_APPROVED'',''TOPIC_REJECTED'',''DEADLINE_REMINDER'',''REGISTRATION_OPENED'',''RESULT_PUBLISHED''))');
PREPARE result_email_statement FROM @result_email_sql;
EXECUTE result_email_statement;
DEALLOCATE PREPARE result_email_statement;

SET @result_email_sql = IF(EXISTS (
  SELECT 1 FROM information_schema.check_constraints
  WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_email_outbox_01'
    AND check_clause LIKE '%RESULT_PUBLISHED%'
), 'SELECT 1', 'ALTER TABLE `email_outbox` DROP CHECK `ck_email_outbox_01`, ADD CONSTRAINT `ck_email_outbox_01` CHECK (`event_type` IN (''TASK_ASSIGNED'',''TOPIC_APPROVED'',''TOPIC_REJECTED'',''DEADLINE_REMINDER'',''REGISTRATION_OPENED'',''RESULT_PUBLISHED''))');
PREPARE result_email_statement FROM @result_email_sql;
EXECUTE result_email_statement;
DEALLOCATE PREPARE result_email_statement;
