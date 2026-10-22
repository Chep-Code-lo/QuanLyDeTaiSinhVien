-- Intentionally no data change.
--
-- Earlier deployments used this version number to delete invitation-cancellation
-- notifications. Historical notifications are application data and must be
-- retained, so Flyway records this migration without deleting any existing row.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

SELECT 1 AS migration_preserves_existing_notification_history;
