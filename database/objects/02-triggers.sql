-- MySQL 8.4 bootstrap: business-rule triggers, after tables and views.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
USE `ql_de_tai_sinh_vien`;

-- PHẦN 5: TRIGGER BẢO VỆ ĐIỂM, BỘ TIÊU CHÍ VÀ LỊCH SỬ
-- Không đọc hoặc ghi NEW/OLD của cột generated.
-- Lỗi 45000 là lỗi nghiệp vụ; Spring cần chuyển thành thông báo phù hợp.
DELIMITER $$
CREATE TRIGGER tr_audit_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit log is append-only';
END$$
CREATE TRIGGER tr_audit_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit log is append-only';
END$$
CREATE TRIGGER tr_result_inputs_no_update BEFORE UPDATE ON result_inputs FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Result snapshot is immutable; create a new result version';
END$$
CREATE TRIGGER tr_result_inputs_no_delete BEFORE DELETE ON result_inputs FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Result snapshot is immutable';
END$$
CREATE TRIGGER tr_result_members_no_update BEFORE UPDATE ON result_members FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Result member snapshot is immutable';
END$$
CREATE TRIGGER tr_result_members_no_delete BEFORE DELETE ON result_members FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Result member snapshot is immutable';
END$$
CREATE TRIGGER tr_result_version_insert BEFORE INSERT ON result_versions FOR EACH ROW
BEGIN
 IF NEW.status <> 'FINALIZED' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Create FINALIZED first; publish only after snapshots exist';
 END IF;
END$$
CREATE TRIGGER tr_result_version_update BEFORE UPDATE ON result_versions FOR EACH ROW
BEGIN
 IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.assignment_id <=> OLD.assignment_id)
    OR NOT (NEW.version_no <=> OLD.version_no) OR NOT (NEW.final_score <=> OLD.final_score)
    OR NOT (NEW.calculation_rule <=> OLD.calculation_rule)
    OR NOT (NEW.finalized_by <=> OLD.finalized_by) OR NOT (NEW.finalized_at <=> OLD.finalized_at) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Result source fields are immutable';
 END IF;
 IF OLD.status = 'FINALIZED' AND NEW.status = 'PUBLISHED' THEN
  IF NOT EXISTS (SELECT 1 FROM result_inputs WHERE result_id = OLD.id)
     OR NOT EXISTS (SELECT 1 FROM result_members WHERE result_id = OLD.id) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Publication requires score and member snapshots';
  END IF;
 ELSEIF OLD.status = 'PUBLISHED' AND NEW.status = 'SUPERSEDED' THEN
  IF NOT (NEW.published_by <=> OLD.published_by) OR NOT (NEW.published_at <=> OLD.published_at) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Keep original publication metadata';
  END IF;
 ELSEIF NEW.status = OLD.status THEN
  IF NOT (NEW.published_by <=> OLD.published_by) OR NOT (NEW.published_at <=> OLD.published_at) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Publication metadata cannot be rewritten';
  END IF;
 ELSE
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid result status transition';
 END IF;
END$$
CREATE TRIGGER tr_result_version_no_delete BEFORE DELETE ON result_versions FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Keep result history; do not delete result versions';
END$$
CREATE TRIGGER tr_result_inputs_insert BEFORE INSERT ON result_inputs FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 DECLARE v_assignment BIGINT;
 SELECT status, assignment_id INTO v_state, v_assignment FROM result_versions
 WHERE id = NEW.result_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'FINALIZED' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Add snapshots only before publication';
 END IF;
 IF NOT EXISTS (
  SELECT 1 FROM score_sheets ss JOIN evaluation_assignments ea ON ea.id = ss.evaluation_id
  JOIN v_score_sheet_totals st ON st.sheet_id = ss.id
  WHERE ss.id = NEW.sheet_id AND ss.status = 'LOCKED' AND ea.status = 'ACTIVE'
    AND ea.assignment_id = v_assignment AND ea.component_id = NEW.component_id
    AND ss.version = NEW.sheet_version AND st.total_score = NEW.score_snapshot
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Snapshot source does not match a locked score sheet';
 END IF;
END$$
CREATE TRIGGER tr_result_members_insert BEFORE INSERT ON result_members FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 DECLARE v_assignment BIGINT;
 SELECT status, assignment_id INTO v_state, v_assignment FROM result_versions
 WHERE id = NEW.result_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'FINALIZED' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Add member snapshots only before publication';
 END IF;
 IF NOT EXISTS (
  SELECT 1 FROM project_assignments pa JOIN group_members gm ON gm.group_id = pa.group_id
  JOIN students s ON s.id = gm.student_id JOIN users u ON u.id = s.user_id
  WHERE pa.id = v_assignment AND gm.student_id = NEW.student_id AND gm.left_at IS NULL
   AND CAST(s.student_code AS BINARY) = CAST(NEW.student_code_snapshot AS BINARY)
   AND CAST(u.full_name AS BINARY) = CAST(NEW.full_name_snapshot AS BINARY)
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Member snapshot does not match the current project group';
 END IF;
END$$
CREATE TRIGGER tr_score_detail_insert BEFORE INSERT ON score_details FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 SELECT status INTO v_state FROM score_sheets WHERE id = NEW.sheet_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Edit criteria only while the score sheet is DRAFT';
 END IF;
END$$
CREATE TRIGGER tr_score_detail_update BEFORE UPDATE ON score_details FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 IF NEW.sheet_id <> OLD.sheet_id OR NEW.rubric_id <> OLD.rubric_id OR NEW.criterion_id <> OLD.criterion_id THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Do not move a score detail to another sheet or criterion';
 END IF;
 SELECT status INTO v_state FROM score_sheets WHERE id = OLD.sheet_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Reopen score sheet with approval before editing';
 END IF;
END$$
CREATE TRIGGER tr_score_detail_delete BEFORE DELETE ON score_details FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 SELECT status INTO v_state FROM score_sheets WHERE id = OLD.sheet_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Do not remove submitted scores';
 END IF;
END$$
CREATE TRIGGER tr_criterion_insert BEFORE INSERT ON rubric_criteria FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 SELECT status INTO v_state FROM rubrics WHERE id = NEW.rubric_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Criteria can be added only to a DRAFT rubric';
 END IF;
END$$
CREATE TRIGGER tr_criterion_update BEFORE UPDATE ON rubric_criteria FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 IF NEW.rubric_id <> OLD.rubric_id THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Do not move a rubric criterion';
 END IF;
 SELECT status INTO v_state FROM rubrics WHERE id = OLD.rubric_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Locked rubric criteria are immutable';
 END IF;
END$$
CREATE TRIGGER tr_criterion_delete BEFORE DELETE ON rubric_criteria FOR EACH ROW
BEGIN
 DECLARE v_state VARCHAR(20);
 SELECT status INTO v_state FROM rubrics WHERE id = OLD.rubric_id FOR UPDATE;
 IF v_state IS NULL OR v_state <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Locked rubric criteria are immutable';
 END IF;
END$$
CREATE TRIGGER tr_rubric_update BEFORE UPDATE ON rubrics FOR EACH ROW
BEGIN
 DECLARE v_total DECIMAL(8,2);
 IF OLD.status = 'LOCKED' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Locked rubric is immutable; create a new version';
 END IF;
 IF NEW.status = 'LOCKED' THEN
  SELECT SUM(max_score) INTO v_total FROM rubric_criteria WHERE rubric_id = OLD.id;
  IF v_total IS NULL OR v_total <> 10 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rubric criteria must total exactly 10 points';
  END IF;
 END IF;
END$$
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

CREATE TRIGGER tr_evaluation_assignment_insert BEFORE INSERT ON evaluation_assignments FOR EACH ROW
BEGIN
 DECLARE v_type VARCHAR(20);
 SELECT component_type INTO v_type FROM assessment_components WHERE id = NEW.component_id;
 IF v_type = 'REVIEW' AND (NEW.evaluator_role <> 'REVIEWER' OR NEW.defense_id IS NOT NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Review assignment must use REVIEWER and no defense session';
 ELSEIF v_type = 'COMMITTEE' AND (NEW.evaluator_role <> 'COMMITTEE_MEMBER' OR NEW.defense_id IS NULL
   OR NOT EXISTS (SELECT 1 FROM defense_sessions ds JOIN committee_members cm ON cm.committee_id = ds.committee_id
                  WHERE ds.id = NEW.defense_id AND cm.lecturer_id = NEW.lecturer_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Committee evaluator must belong to the assigned committee';
 END IF;
END$$

CREATE TRIGGER tr_evaluation_assignment_update BEFORE UPDATE ON evaluation_assignments FOR EACH ROW
BEGIN
 DECLARE v_type VARCHAR(20);
 SELECT component_type INTO v_type FROM assessment_components WHERE id = NEW.component_id;
 IF v_type = 'REVIEW' AND (NEW.evaluator_role <> 'REVIEWER' OR NEW.defense_id IS NOT NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Review assignment must use REVIEWER and no defense session';
 ELSEIF v_type = 'COMMITTEE' AND (NEW.evaluator_role <> 'COMMITTEE_MEMBER' OR NEW.defense_id IS NULL
   OR NOT EXISTS (SELECT 1 FROM defense_sessions ds JOIN committee_members cm ON cm.committee_id = ds.committee_id
                  WHERE ds.id = NEW.defense_id AND cm.lecturer_id = NEW.lecturer_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Committee evaluator must belong to the assigned committee';
 END IF;
END$$

CREATE TRIGGER tr_defense_session_insert BEFORE INSERT ON defense_sessions FOR EACH ROW
BEGIN
 IF EXISTS (
  SELECT 1 FROM project_assignments pa JOIN topic_supervisors ts ON ts.topic_id = pa.topic_id
  JOIN committee_members cm ON cm.committee_id = NEW.committee_id AND cm.lecturer_id = ts.lecturer_id
  WHERE pa.id = NEW.assignment_id
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A supervisor cannot join the committee of the supervised topic';
 END IF;
END$$

CREATE TRIGGER tr_defense_session_update BEFORE UPDATE ON defense_sessions FOR EACH ROW
BEGIN
 IF NEW.assignment_id <> OLD.assignment_id OR NEW.committee_id <> OLD.committee_id THEN
  IF EXISTS (
   SELECT 1 FROM project_assignments pa JOIN topic_supervisors ts ON ts.topic_id = pa.topic_id
   JOIN committee_members cm ON cm.committee_id = NEW.committee_id AND cm.lecturer_id = ts.lecturer_id
   WHERE pa.id = NEW.assignment_id
  ) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A supervisor cannot join the committee of the supervised topic';
  END IF;
 END IF;
END$$

CREATE TRIGGER tr_topic_publish_insert BEFORE INSERT ON topics FOR EACH ROW
BEGIN
 IF NEW.status = 'PUBLISHED' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Create and approve the topic, assign supervisors, then publish by update';
 END IF;
END$$

CREATE TRIGGER tr_topic_publish_update BEFORE UPDATE ON topics FOR EACH ROW
BEGIN
 IF OLD.status <> 'PUBLISHED' AND NEW.status = 'PUBLISHED' THEN
  IF NOT EXISTS (SELECT 1 FROM topic_supervisors WHERE topic_id = OLD.id AND slot_no = 1)
     OR (SELECT COUNT(*) FROM topic_supervisors WHERE topic_id = OLD.id) > 2 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Assign one primary and at most one secondary supervisor before publication';
  END IF;
 END IF;
END$$
CREATE TRIGGER tr_rubric_insert BEFORE INSERT ON rubrics FOR EACH ROW
BEGIN
 IF NEW.status <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Create a DRAFT rubric and add criteria before locking';
 END IF;
END$$
CREATE TRIGGER tr_score_sheet_insert BEFORE INSERT ON score_sheets FOR EACH ROW
BEGIN
 IF NEW.status <> 'DRAFT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Create a DRAFT score sheet before entering scores';
 END IF;
END$$
DELIMITER ;


