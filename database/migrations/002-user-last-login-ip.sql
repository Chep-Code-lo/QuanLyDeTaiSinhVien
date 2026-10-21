-- Store the address from the most recent successful login for account administration.
SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'last_login_ip'
);
SET @statement = IF(
  @column_exists = 0,
  'ALTER TABLE `users` ADD COLUMN `last_login_ip` VARCHAR(45) NULL DEFAULT NULL COMMENT ''Địa chỉ IP ở lần đăng nhập gần nhất'' AFTER `last_login_at`',
  'SELECT 1'
);
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
