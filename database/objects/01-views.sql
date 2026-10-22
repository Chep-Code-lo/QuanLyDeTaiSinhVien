-- MySQL 8.4 bootstrap: read-model views, after tables and foreign keys.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
USE `ql_de_tai_sinh_vien`;

-- PHẦN 4: VIEW PHỤC VỤ TRA CỨU VÀ THỐNG KÊ
-- SQL SECURITY INVOKER: view sử dụng quyền của tài khoản MySQL đang gọi.
-- View không thay Spring Security. Backend phải lọc theo người đăng nhập.
CREATE SQL SECURITY INVOKER VIEW v_current_group_members AS
SELECT gm.group_id, gm.period_id, gm.student_id, s.student_code,
       u.full_name, gm.slot_no, gm.member_role, gm.joined_at
FROM group_members gm JOIN students s ON s.id = gm.student_id
JOIN users u ON u.id = s.user_id WHERE gm.left_at IS NULL;

CREATE SQL SECURITY INVOKER VIEW v_topic_catalog AS
SELECT t.id AS topic_id, t.period_id, t.department_id, t.code, t.title,
       t.description, t.visibility_type, t.reserved_group_id, t.published_at,
       a.supervisor_names, pa.group_id AS assigned_group_id
FROM topics t
LEFT JOIN (
 SELECT ts.topic_id,
        GROUP_CONCAT(u.full_name ORDER BY ts.slot_no SEPARATOR ', ') AS supervisor_names
 FROM topic_supervisors ts JOIN lecturers l ON l.id = ts.lecturer_id
 JOIN users u ON u.id = l.user_id GROUP BY ts.topic_id
) a ON a.topic_id = t.id
LEFT JOIN project_assignments pa ON pa.topic_id = t.id
WHERE t.status = 'PUBLISHED';

CREATE SQL SECURITY INVOKER VIEW v_project_progress AS
SELECT pa.id AS assignment_id, pa.group_id, pa.topic_id, pa.period_id, pa.status,
       COUNT(m.id) AS milestone_count,
       COALESCE(SUM(m.status = 'ACCEPTED'), 0) AS accepted_count,
       CASE WHEN COUNT(m.id) = 0 THEN 0
            ELSE ROUND(100.0 * SUM(m.status = 'ACCEPTED') / COUNT(m.id), 2)
       END AS progress_percent
FROM project_assignments pa LEFT JOIN milestones m ON m.assignment_id = pa.id
GROUP BY pa.id, pa.group_id, pa.topic_id, pa.period_id, pa.status;

CREATE SQL SECURITY INVOKER VIEW v_committee_projects AS
SELECT ec.id AS committee_id, ec.faculty_id, ec.name AS committee_name,
       ds.id AS defense_id, ds.starts_at, ds.ends_at, ds.location,
       pa.id AS assignment_id, sg.id AS group_id, sg.name AS group_name,
       t.id AS topic_id, t.code AS topic_code, t.title AS topic_title
FROM evaluation_committees ec
JOIN defense_sessions ds ON ds.committee_id = ec.id AND ds.period_id = ec.period_id
JOIN project_assignments pa ON pa.id = ds.assignment_id
JOIN student_groups sg ON sg.id = pa.group_id
JOIN topics t ON t.id = pa.topic_id
WHERE ds.status <> 'CANCELLED';

CREATE SQL SECURITY INVOKER VIEW v_score_sheet_totals AS
SELECT ss.id AS sheet_id, ss.evaluation_id, ss.rubric_id, ss.status, ss.version,
       COALESCE(d.scored_count, 0) AS scored_count,
       COALESCE(c.required_count, 0) AS required_count,
       CASE WHEN c.required_count > 0 AND c.rubric_max = 10
                  AND d.scored_count = c.required_count
            THEN d.total_score ELSE NULL END AS total_score
FROM score_sheets ss
LEFT JOIN (SELECT sheet_id, COUNT(*) AS scored_count, SUM(score) AS total_score
           FROM score_details GROUP BY sheet_id) d ON d.sheet_id = ss.id
LEFT JOIN (SELECT rubric_id, COUNT(*) AS required_count, SUM(max_score) AS rubric_max
           FROM rubric_criteria GROUP BY rubric_id) c ON c.rubric_id = ss.rubric_id;

CREATE SQL SECURITY INVOKER VIEW v_component_scores AS
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

CREATE SQL SECURITY INVOKER VIEW v_published_results AS
SELECT rv.id AS result_id, rv.assignment_id, rv.version_no,
       rm.student_id, rm.student_code_snapshot, rm.full_name_snapshot,
       rv.final_score, rv.calculation_rule, rv.published_at
FROM result_versions rv JOIN result_members rm ON rm.result_id = rv.id
WHERE rv.status = 'PUBLISHED';

