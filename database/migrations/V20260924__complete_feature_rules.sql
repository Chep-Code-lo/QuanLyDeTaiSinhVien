-- Apply once to an existing MySQL 8.4 database created before the feature-completion update.
-- Fresh Docker volumes receive the same shape directly from ql_de_tai_sinh_vien_schema.sql.
-- This migration deliberately replaces named CHECK constraints, so do not rerun it
-- after a successful application. See README.md for the Docker-volume procedure.

ALTER TABLE registration_periods
  ADD COLUMN min_group_members TINYINT NOT NULL DEFAULT 1 AFTER defense_date,
  ADD COLUMN max_group_members TINYINT NOT NULL DEFAULT 3 AFTER min_group_members,
  ADD COLUMN invitation_expiry_hours SMALLINT NOT NULL DEFAULT 72 AFTER max_group_members,
  ADD COLUMN min_committee_members TINYINT NOT NULL DEFAULT 3 AFTER invitation_expiry_hours,
  ADD COLUMN max_committee_members TINYINT NOT NULL DEFAULT 5 AFTER min_committee_members;

ALTER TABLE registration_periods
  ADD CONSTRAINT ck_registration_periods_10
    CHECK (min_group_members BETWEEN 1 AND 10 AND max_group_members BETWEEN min_group_members AND 10),
  ADD CONSTRAINT ck_registration_periods_11
    CHECK (invitation_expiry_hours BETWEEN 1 AND 720),
  ADD CONSTRAINT ck_registration_periods_12
    CHECK (min_committee_members BETWEEN 1 AND 10 AND max_committee_members BETWEEN min_committee_members AND 10);

ALTER TABLE group_members DROP CHECK ck_group_members_01;
ALTER TABLE group_members
  ADD CONSTRAINT ck_group_members_01 CHECK (slot_no BETWEEN 1 AND 10);

ALTER TABLE committee_members DROP CHECK ck_committee_members_01;
ALTER TABLE committee_members
  ADD CONSTRAINT ck_committee_members_01 CHECK (slot_no BETWEEN 1 AND 10);

ALTER TABLE assessment_components DROP CHECK ck_assessment_components_01;
ALTER TABLE assessment_components DROP CHECK ck_assessment_components_02;
ALTER TABLE assessment_components
  ADD CONSTRAINT ck_assessment_components_01 CHECK (expected_evaluators BETWEEN 1 AND 10),
  ADD CONSTRAINT ck_assessment_components_02
    CHECK (component_type <> 'COMMITTEE' OR expected_evaluators BETWEEN 1 AND 10);
