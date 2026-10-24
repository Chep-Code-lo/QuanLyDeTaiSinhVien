-- Align period rules with the course rubric. Membership/history rows are preserved.
-- Existing projects remain intact; undersized unassigned groups must recruit members
-- before registering, and oversized groups must remove members before registering.
UPDATE registration_periods
SET min_group_members = 3, max_group_members = 5, version = version + 1
WHERE min_group_members < 3 OR min_group_members > 5
   OR max_group_members < min_group_members OR max_group_members > 5;

ALTER TABLE registration_periods
  ALTER COLUMN min_group_members SET DEFAULT 3,
  ALTER COLUMN max_group_members SET DEFAULT 5;

-- Use a new name so this also works with a freshly bootstrapped schema.
SET @group_rule_sql = IF(EXISTS (
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'registration_periods'
    AND constraint_name = 'ck_registration_periods_rubric_group_size'
), 'SELECT 1', 'ALTER TABLE registration_periods ADD CONSTRAINT ck_registration_periods_rubric_group_size CHECK (min_group_members BETWEEN 3 AND 5 AND max_group_members BETWEEN min_group_members AND 5)');
PREPARE group_rule_statement FROM @group_rule_sql;
EXECUTE group_rule_statement;
DEALLOCATE PREPARE group_rule_statement;

UPDATE student_groups g
JOIN registration_periods p ON p.id = g.period_id
LEFT JOIN (
  SELECT group_id, COUNT(*) AS member_count
  FROM group_members WHERE left_at IS NULL GROUP BY group_id
) m ON m.group_id = g.id
SET g.status = CASE
      WHEN COALESCE(m.member_count, 0) BETWEEN p.min_group_members AND p.max_group_members
        THEN 'ACTIVE' ELSE 'FORMING' END,
    g.version = g.version + 1
WHERE g.status IN ('FORMING', 'ACTIVE')
  AND g.status <> CASE
      WHEN COALESCE(m.member_count, 0) BETWEEN p.min_group_members AND p.max_group_members
        THEN 'ACTIVE' ELSE 'FORMING' END;
