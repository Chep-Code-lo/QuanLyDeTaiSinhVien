-- Upgrade existing MySQL databases without recreating tables or changing schedules.
-- Safe to run again: existing columns, values and constraints are preserved.
SET NAMES utf8mb4;

SELECT GROUP_CONCAT(CONCAT('ADD COLUMN ', needed.definition) ORDER BY needed.position SEPARATOR ', ')
INTO @period_rule_columns
FROM (
    SELECT 1 AS position, 'min_group_members' AS column_name,
           '`min_group_members` TINYINT NOT NULL DEFAULT 1' AS definition
    UNION ALL SELECT 2, 'max_group_members', '`max_group_members` TINYINT NOT NULL DEFAULT 3'
    UNION ALL SELECT 3, 'invitation_expiry_hours', '`invitation_expiry_hours` SMALLINT NOT NULL DEFAULT 72'
    UNION ALL SELECT 4, 'min_committee_members', '`min_committee_members` TINYINT NOT NULL DEFAULT 3'
    UNION ALL SELECT 5, 'max_committee_members', '`max_committee_members` TINYINT NOT NULL DEFAULT 5'
) needed
LEFT JOIN information_schema.COLUMNS existing
    ON existing.TABLE_SCHEMA = DATABASE()
    AND existing.TABLE_NAME = 'registration_periods'
    AND existing.COLUMN_NAME = needed.column_name
WHERE existing.COLUMN_NAME IS NULL;

SET @period_rules_sql = IF(@period_rule_columns IS NULL,
    'DO 0', CONCAT('ALTER TABLE `registration_periods` ', @period_rule_columns));
PREPARE period_rules_migration FROM @period_rules_sql;
EXECUTE period_rules_migration;
DEALLOCATE PREPARE period_rules_migration;

SELECT GROUP_CONCAT(CONCAT('ADD CONSTRAINT `', needed.constraint_name, '` CHECK (', needed.expression, ')')
                    ORDER BY needed.position SEPARATOR ', ')
INTO @period_rule_constraints
FROM (
    SELECT 1 AS position, 'ck_registration_periods_10' AS constraint_name,
           'min_group_members BETWEEN 1 AND 10 AND max_group_members BETWEEN min_group_members AND 10' AS expression
    UNION ALL SELECT 2, 'ck_registration_periods_11', 'invitation_expiry_hours BETWEEN 1 AND 720'
    UNION ALL SELECT 3, 'ck_registration_periods_12',
        'min_committee_members BETWEEN 1 AND 10 AND max_committee_members BETWEEN min_committee_members AND 10'
) needed
LEFT JOIN information_schema.TABLE_CONSTRAINTS existing
    ON existing.CONSTRAINT_SCHEMA = DATABASE()
    AND existing.TABLE_NAME = 'registration_periods'
    AND existing.CONSTRAINT_NAME = needed.constraint_name
WHERE existing.CONSTRAINT_NAME IS NULL;

SET @period_rules_sql = IF(@period_rule_constraints IS NULL,
    'DO 0', CONCAT('ALTER TABLE `registration_periods` ', @period_rule_constraints));
PREPARE period_rules_migration FROM @period_rules_sql;
EXECUTE period_rules_migration;
DEALLOCATE PREPARE period_rules_migration;
