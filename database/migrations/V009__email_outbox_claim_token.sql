-- Nullable for legacy rows; each new claim receives a fresh fencing token.
SET @claim_token_sql = IF(EXISTS (
  SELECT 1 FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'email_outbox' AND column_name = 'claim_token'
), 'SELECT 1', 'ALTER TABLE email_outbox ADD COLUMN claim_token VARCHAR(36) NULL DEFAULT NULL');
PREPARE claim_token_statement FROM @claim_token_sql;
EXECUTE claim_token_statement;
DEALLOCATE PREPARE claim_token_statement;
