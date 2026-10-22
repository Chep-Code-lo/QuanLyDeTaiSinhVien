-- Store the address from the most recent successful login for account administration.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

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
