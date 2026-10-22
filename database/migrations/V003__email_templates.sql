-- Add personal templates without changing or deleting existing notification settings/outbox.
-- CREATE TABLE IF NOT EXISTS supports adoption by a database upgraded before
-- Flyway began tracking migrations.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE TABLE IF NOT EXISTS `email_templates` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `owner_id` BIGINT NOT NULL,
  `name` VARCHAR(150) NOT NULL,
  `sender_name` VARCHAR(150) NOT NULL,
  `from_email` VARCHAR(254) NOT NULL,
  `reply_to_email` VARCHAR(254) NULL,
  `subject_template` VARCHAR(500) NOT NULL,
  `body_template` MEDIUMTEXT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `ix_email_templates_owner_updated` (`owner_id`, `updated_at`, `id`),
  CONSTRAINT `fk_email_templates_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_email_templates_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
  CONSTRAINT `ck_email_templates_version` CHECK (`version` >= 0)
) ENGINE=InnoDB COMMENT='Mẫu email cá nhân của giảng viên và quản trị viên';
