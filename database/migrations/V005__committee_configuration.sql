-- Preserve existing committees while adding their selected rubric and editable role labels.
-- The guards support adoption by a database upgraded before Flyway tracked it.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

SET @committee_configuration_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'committee_members'
    AND COLUMN_NAME = 'role_label'
), 'SELECT 1', 'ALTER TABLE `committee_members` ADD COLUMN `role_label` VARCHAR(100) NULL DEFAULT NULL');
PREPARE committee_configuration_statement FROM @committee_configuration_sql;
EXECUTE committee_configuration_statement;
DEALLOCATE PREPARE committee_configuration_statement;

SET @committee_configuration_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'evaluation_committees'
    AND COLUMN_NAME = 'rubric_id'
), 'SELECT 1', 'ALTER TABLE `evaluation_committees` ADD COLUMN `rubric_id` BIGINT NULL DEFAULT NULL');
PREPARE committee_configuration_statement FROM @committee_configuration_sql;
EXECUTE committee_configuration_statement;
DEALLOCATE PREPARE committee_configuration_statement;

SET @committee_configuration_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'evaluation_committees'
    AND CONSTRAINT_NAME = 'fk_evaluation_committees_rubric'
), 'SELECT 1', 'ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_rubric` FOREIGN KEY (`rubric_id`) REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT');
PREPARE committee_configuration_statement FROM @committee_configuration_sql;
EXECUTE committee_configuration_statement;
DEALLOCATE PREPARE committee_configuration_statement;

-- A committee score requires every currently configured committee member.
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_component_scores AS
SELECT ea.assignment_id, ea.component_id,
       MAX(CASE WHEN ac.component_type = 'COMMITTEE' THEN COALESCE(roster.member_count, 0)
                ELSE ac.expected_evaluators END) AS expected_evaluators,
       COUNT(*) AS assigned_count,
       SUM(COALESCE(st.status IN ('SUBMITTED','LOCKED') AND st.total_score IS NOT NULL, 0)) AS completed_count,
       CASE WHEN COUNT(*) = MAX(CASE WHEN ac.component_type = 'COMMITTEE' THEN COALESCE(roster.member_count, 0)
                                    ELSE ac.expected_evaluators END)
             AND SUM(COALESCE(st.status IN ('SUBMITTED','LOCKED') AND st.total_score IS NOT NULL, 0))
                 = MAX(CASE WHEN ac.component_type = 'COMMITTEE' THEN COALESCE(roster.member_count, 0)
                            ELSE ac.expected_evaluators END)
             AND (ac.component_type <> 'COMMITTEE' OR (COUNT(DISTINCT ds.id) = 1 AND COUNT(ds.id) = COUNT(*)))
            THEN AVG(st.total_score) ELSE NULL END AS component_score
FROM evaluation_assignments ea JOIN assessment_components ac ON ac.id = ea.component_id
LEFT JOIN defense_sessions ds ON ds.id = ea.defense_id
LEFT JOIN (SELECT committee_id, COUNT(*) AS member_count FROM committee_members GROUP BY committee_id) roster
       ON roster.committee_id = ds.committee_id
LEFT JOIN v_score_sheet_totals st ON st.evaluation_id = ea.id
WHERE ea.status = 'ACTIVE' AND ac.status = 'LOCKED'
GROUP BY ea.assignment_id, ea.component_id, ac.expected_evaluators, ac.component_type;
