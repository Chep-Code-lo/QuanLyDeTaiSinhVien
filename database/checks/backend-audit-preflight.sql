-- Read-only checks for an existing MySQL database. Select the intended database first.
-- These statements do not apply migrations or change existing rows.
SELECT DATABASE() AS database_name, VERSION() AS mysql_version,
       @@transaction_isolation AS transaction_isolation;

SELECT table_name, index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND non_unique = 0
  AND index_name IN ('uq_group_members_01', 'uq_group_members_02',
      'uq_topic_registrations_01', 'uq_project_assignments_01',
      'uq_project_assignments_02', 'uq_project_assignments_03',
      'uq_rubrics_01', 'uq_evaluation_assignments_01', 'uq_score_sheets_01',
      'uq_score_details_01', 'uq_report_submissions_01', 'uq_grade_change_requests_01')
GROUP BY table_name, index_name ORDER BY table_name, index_name;

-- Every query below should return no rows. Investigate results; never delete history automatically.
SELECT group_id, COUNT(*) AS active_registrations
FROM topic_registrations WHERE status IN ('PENDING', 'APPROVED')
GROUP BY group_id HAVING COUNT(*) > 1;

SELECT period_id, student_id, COUNT(*) AS active_memberships
FROM group_members WHERE left_at IS NULL
GROUP BY period_id, student_id HAVING COUNT(*) > 1;

SELECT m.student_id, COUNT(*) AS memberships_in_open_groups
FROM group_members m JOIN student_groups g ON g.id = m.group_id
WHERE m.left_at IS NULL AND g.status <> 'CLOSED'
GROUP BY m.student_id HAVING COUNT(*) > 1;

SELECT component_id, version_no, COUNT(*) AS duplicates FROM rubrics
GROUP BY component_id, version_no HAVING COUNT(*) > 1;

SELECT assignment_id, component_id, lecturer_id, COUNT(*) AS duplicates
FROM evaluation_assignments WHERE status = 'ACTIVE'
GROUP BY assignment_id, component_id, lecturer_id HAVING COUNT(*) > 1;

SELECT e.assignment_id, e.component_id, COUNT(*) AS active_evaluators, c.expected_evaluators
FROM evaluation_assignments e JOIN assessment_components c ON c.id = e.component_id
WHERE e.status = 'ACTIVE'
GROUP BY e.assignment_id, e.component_id, c.expected_evaluators
HAVING COUNT(*) > c.expected_evaluators;

SELECT evaluation_id, COUNT(*) AS duplicates FROM score_sheets
GROUP BY evaluation_id HAVING COUNT(*) > 1;

SELECT assignment_id, COUNT(*) AS unpublished_finalizations FROM result_versions
WHERE status = 'FINALIZED' GROUP BY assignment_id HAVING COUNT(*) > 1;
