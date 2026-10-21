-- The evaluator count is no longer configured with the rubric.  Existing column values are
-- retained for compatibility only; all workflow checks derive the count from actual assignments.

SET @legacy_evaluator_column_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'assessment_components'
    AND COLUMN_NAME = 'expected_evaluators'
    AND (COLUMN_DEFAULT IS NULL
         OR COLUMN_COMMENT <> 'Tương thích dữ liệu cũ; số người chấm được suy ra từ phân công')
), 'ALTER TABLE `assessment_components` MODIFY COLUMN `expected_evaluators` TINYINT NOT NULL DEFAULT 1 COMMENT ''Tương thích dữ liệu cũ; số người chấm được suy ra từ phân công''', 'SELECT 1');
PREPARE legacy_evaluator_column_statement FROM @legacy_evaluator_column_sql;
EXECUTE legacy_evaluator_column_statement;
DEALLOCATE PREPARE legacy_evaluator_column_statement;

-- For normal components, every evaluator that is actually assigned must finish scoring.
-- Committee components additionally require the full current committee roster.
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_component_scores AS
SELECT ea.assignment_id, ea.component_id,
       CASE WHEN ac.component_type = 'COMMITTEE' THEN COALESCE(MAX(roster.member_count), 0)
            ELSE COUNT(*) END AS expected_evaluators,
       COUNT(*) AS assigned_count,
       SUM(COALESCE(st.status IN ('SUBMITTED','LOCKED') AND st.total_score IS NOT NULL, 0)) AS completed_count,
       CASE WHEN COUNT(*) = CASE WHEN ac.component_type = 'COMMITTEE' THEN COALESCE(MAX(roster.member_count), 0)
                                 ELSE COUNT(*) END
             AND SUM(COALESCE(st.status IN ('SUBMITTED','LOCKED') AND st.total_score IS NOT NULL, 0))
                 = CASE WHEN ac.component_type = 'COMMITTEE' THEN COALESCE(MAX(roster.member_count), 0)
                        ELSE COUNT(*) END
             AND (ac.component_type <> 'COMMITTEE' OR (COUNT(DISTINCT ds.id) = 1 AND COUNT(ds.id) = COUNT(*)))
            THEN AVG(st.total_score) ELSE NULL END AS component_score
FROM evaluation_assignments ea JOIN assessment_components ac ON ac.id = ea.component_id
LEFT JOIN defense_sessions ds ON ds.id = ea.defense_id
LEFT JOIN (SELECT committee_id, COUNT(*) AS member_count FROM committee_members GROUP BY committee_id) roster
       ON roster.committee_id = ds.committee_id
LEFT JOIN v_score_sheet_totals st ON st.evaluation_id = ea.id
WHERE ea.status = 'ACTIVE' AND ac.status = 'LOCKED'
GROUP BY ea.assignment_id, ea.component_id, ac.component_type;

DROP TRIGGER IF EXISTS tr_score_sheet_update;
DELIMITER $$
CREATE TRIGGER tr_score_sheet_update BEFORE UPDATE ON score_sheets FOR EACH ROW
BEGIN
 DECLARE v_count INT;
 DECLARE v_required INT;
 DECLARE v_max DECIMAL(8,2);
 DECLARE v_component_type VARCHAR(20);
 DECLARE v_assignment BIGINT;
 IF NEW.evaluation_id <> OLD.evaluation_id OR NEW.rubric_id <> OLD.rubric_id THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Do not move a score sheet to another evaluation';
 END IF;
 IF OLD.status IN ('SUBMITTED','LOCKED') AND NEW.status = 'DRAFT' THEN
  IF NOT EXISTS (SELECT 1 FROM grade_change_requests WHERE sheet_id = OLD.id AND status = 'APPROVED') THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'An approved grade change request is required';
  END IF;
 ELSEIF OLD.status IN ('SUBMITTED','LOCKED') THEN
  IF NOT (NEW.overall_comment <=> OLD.overall_comment)
     OR NOT (NEW.submitted_at <=> OLD.submitted_at)
     OR NOT ((NEW.status = OLD.status) OR (OLD.status = 'SUBMITTED' AND NEW.status = 'LOCKED')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Submitted score sheet is locked for editing';
  END IF;
 END IF;
 IF OLD.status = 'DRAFT' AND NEW.status <> 'DRAFT' THEN
  IF NEW.status <> 'SUBMITTED' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Submit a DRAFT score sheet before locking it';
  END IF;
  SELECT COUNT(*), SUM(max_score) INTO v_required, v_max FROM rubric_criteria WHERE rubric_id = NEW.rubric_id;
  SELECT COUNT(*) INTO v_count FROM score_details WHERE sheet_id = OLD.id;
  IF v_required = 0 OR v_count <> v_required OR v_max <> 10 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Complete all criteria of a 10-point rubric before submission';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM rubrics WHERE id = NEW.rubric_id AND status = 'LOCKED') THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rubric must be LOCKED before score submission';
  END IF;
  SELECT ac.component_type, ea.assignment_id INTO v_component_type, v_assignment
  FROM evaluation_assignments ea JOIN assessment_components ac ON ac.id = ea.component_id
  WHERE ea.id = NEW.evaluation_id;
  IF v_component_type = 'COMMITTEE' THEN
   IF NOT EXISTS (
     SELECT 1 FROM assessment_components ac
     JOIN project_assignments pa ON pa.period_id = ac.period_id
     WHERE pa.id = v_assignment AND ac.component_type = 'REVIEW' AND ac.status = 'LOCKED'
   ) OR EXISTS (
     SELECT 1 FROM assessment_components ac
     JOIN project_assignments pa ON pa.period_id = ac.period_id
     WHERE pa.id = v_assignment AND ac.component_type = 'REVIEW' AND ac.status = 'LOCKED'
       AND (
        NOT EXISTS (SELECT 1 FROM evaluation_assignments ea
                    WHERE ea.assignment_id = v_assignment AND ea.component_id = ac.id
                      AND ea.status = 'ACTIVE')
        OR EXISTS (SELECT 1 FROM evaluation_assignments ea
                   LEFT JOIN score_sheets ss ON ss.evaluation_id = ea.id
                   LEFT JOIN v_score_sheet_totals st ON st.sheet_id = ss.id
                   WHERE ea.assignment_id = v_assignment AND ea.component_id = ac.id
                     AND ea.status = 'ACTIVE'
                     AND (ss.id IS NULL OR ss.status NOT IN ('SUBMITTED','LOCKED')
                          OR st.total_score IS NULL))
       )
   ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Complete reviewer assignment and review scoring before committee scoring';
   END IF;
  END IF;
 END IF;
END$$
DELIMITER ;
