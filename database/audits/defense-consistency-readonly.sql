-- MySQL 8.4. Run against the intended database, preferably with a SELECT-only account.
-- UTC DATETIME values are converted with +07:00 (no MySQL timezone tables required).
-- No DDL, UPDATE, DELETE, INSERT or automatic repair. Export stdout as TSV.
-- CANCELLED rows are included in historical date checks; only SCHEDULED rows block resources.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;

WITH sessions AS (
    SELECT d.*, p.project_type, p.defense_date,
           REGEXP_REPLACE(LOWER(TRIM(d.location)),
               '^[[:space:]]*(phòng|phong)[[:space:]]+|[[:space:]]+', '') AS room_key
    FROM defense_sessions d JOIN registration_periods p ON p.id = d.period_id
), rosters AS (
    SELECT c.id, c.period_id, c.status, p.min_committee_members, p.max_committee_members,
           COUNT(m.id) AS members, COUNT(DISTINCT m.lecturer_id) AS distinct_members,
           COALESCE(SUM(m.committee_role = 'CHAIR'), 0) AS chairs,
           COALESCE(SUM(m.committee_role = 'SECRETARY'), 0) AS secretaries
    FROM evaluation_committees c JOIN registration_periods p ON p.id = c.period_id
    LEFT JOIN committee_members m ON m.committee_id = c.id
    GROUP BY c.id, c.period_id, c.status, p.min_committee_members, p.max_committee_members
), findings AS (
    SELECT 'GRADUATION_DATE' AS issue, d.id AS record_id, NULL AS related_id, d.period_id,
           CONCAT('status=', d.status, '; configured=', COALESCE(CAST(d.defense_date AS CHAR), 'NULL'),
                  '; start_vn=', DATE_ADD(d.starts_at, INTERVAL 7 HOUR),
                  '; end_vn=', DATE_ADD(d.ends_at, INTERVAL 7 HOUR)) AS detail
    FROM sessions d
    WHERE d.project_type = 'GRADUATION' AND (d.defense_date IS NULL
       OR DATE(DATE_ADD(d.starts_at, INTERVAL 7 HOUR)) <> d.defense_date
       OR DATE(DATE_ADD(d.ends_at, INTERVAL 7 HOUR)) <> d.defense_date)
    UNION ALL
    SELECT 'INVALID_INTERVAL', d.id, NULL, d.period_id, CONCAT(d.starts_at, ' -> ', d.ends_at)
    FROM sessions d WHERE d.starts_at >= d.ends_at
    UNION ALL
    SELECT 'ROOM_OVERLAP', a.id, b.id, a.period_id,
           CONCAT('room_key=', a.room_key, '; other_period=', b.period_id)
    FROM sessions a JOIN sessions b ON a.id < b.id
       AND a.status = 'SCHEDULED' AND b.status = 'SCHEDULED'
       AND a.starts_at < b.ends_at AND a.ends_at > b.starts_at AND a.room_key = b.room_key
    UNION ALL
    SELECT 'COMMITTEE_OVERLAP', a.id, b.id, a.period_id, CONCAT('committee=', a.committee_id)
    FROM sessions a JOIN sessions b ON a.id < b.id AND a.committee_id = b.committee_id
       AND a.status = 'SCHEDULED' AND b.status = 'SCHEDULED'
       AND a.starts_at < b.ends_at AND a.ends_at > b.starts_at
    UNION ALL
    SELECT DISTINCT 'LECTURER_OVERLAP', a.id, b.id, a.period_id, CONCAT('lecturer=', ma.lecturer_id)
    FROM sessions a JOIN sessions b ON a.id < b.id
       AND a.status = 'SCHEDULED' AND b.status = 'SCHEDULED'
       AND a.starts_at < b.ends_at AND a.ends_at > b.starts_at
    JOIN committee_members ma ON ma.committee_id = a.committee_id
    JOIN committee_members mb ON mb.committee_id = b.committee_id AND mb.lecturer_id = ma.lecturer_id
    UNION ALL
    SELECT 'DUPLICATE_PROJECT_SESSION', d.id, d.assignment_id, d.period_id, CONCAT('count=', duplicates.total)
    FROM sessions d JOIN (SELECT assignment_id, COUNT(*) AS total FROM defense_sessions
                         GROUP BY assignment_id HAVING COUNT(*) > 1) duplicates
       ON duplicates.assignment_id = d.assignment_id
    UNION ALL
    SELECT CASE WHEN r.status = 'DRAFT' AND NOT EXISTS (
                    SELECT 1 FROM sessions d WHERE d.committee_id = r.id)
                THEN 'DRAFT_ROSTER_NOT_READY' ELSE 'INVALID_COMMITTEE_ROSTER' END,
           r.id, NULL, r.period_id,
           CONCAT('members=', r.members, '; distinct=', r.distinct_members,
                  '; chairs=', r.chairs, '; secretaries=', r.secretaries,
                  '; allowed=', r.min_committee_members, '-', r.max_committee_members)
    FROM rosters r WHERE r.min_committee_members < 3 OR r.max_committee_members > 5
       OR r.min_committee_members > r.max_committee_members
       OR r.members < r.min_committee_members OR r.members > r.max_committee_members
       OR r.members < 3 OR r.members > 5 OR r.members <> r.distinct_members
       OR r.chairs <> 1 OR r.secretaries <> 1
    UNION ALL
    SELECT 'REVIEW_DEADLINE_AFTER_DEFENSE_DAY', p.id, NULL, p.id,
           CONCAT('review_vn=', DATE_ADD(p.review_deadline_at, INTERVAL 7 HOUR), '; defense=', p.defense_date)
    FROM registration_periods p WHERE p.project_type = 'GRADUATION'
       AND DATE(DATE_ADD(p.review_deadline_at, INTERVAL 7 HOUR)) > p.defense_date
    UNION ALL
    SELECT 'PERIOD_MISMATCH', d.id, d.assignment_id, d.period_id,
           CONCAT('assignment_period=', a.period_id, '; committee_period=', c.period_id)
    FROM sessions d JOIN project_assignments a ON a.id = d.assignment_id
    JOIN evaluation_committees c ON c.id = d.committee_id
    WHERE d.period_id <> a.period_id OR d.period_id <> c.period_id
)
SELECT issue, record_id, related_id, period_id, detail FROM findings
ORDER BY issue, period_id, record_id, related_id;

-- Summary and guard health help distinguish an empty report from an empty database.
SELECT 'TOTAL_SESSIONS' AS metric, COUNT(*) AS value FROM defense_sessions
UNION ALL SELECT 'SCHEDULED_SESSIONS', COUNT(*) FROM defense_sessions WHERE status = 'SCHEDULED'
UNION ALL SELECT 'SCHEDULING_GUARD_ROWS', COUNT(*) FROM defense_schedule_guard WHERE id = 1;
COMMIT;
