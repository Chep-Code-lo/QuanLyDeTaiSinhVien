-- Allow result-publication email events without changing existing rows.
-- Each ALTER replaces its CHECK atomically; re-running this file is safe.

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
