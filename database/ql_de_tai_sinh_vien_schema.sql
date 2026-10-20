
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
CREATE DATABASE `ql_de_tai_sinh_vien`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `ql_de_tai_sinh_vien`;

-- PHẦN 1: BẢNG, KHÓA CHÍNH, UNIQUE, CHECK


-- 01. Khoa
CREATE TABLE `faculties` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `code` VARCHAR(20) NOT NULL COMMENT 'Mã khoa, UQ',
  `name` VARCHAR(150) NOT NULL COMMENT 'Tên khoa',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT 'Mặc định 1; chỉ 0 hoặc 1',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_faculties_01` UNIQUE (`code`),
  CONSTRAINT `ck_faculties_01` CHECK (is_active IN (0,1))
) ENGINE=InnoDB COMMENT='Khoa';


-- 02. Bộ môn
CREATE TABLE `departments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `faculty_id` BIGINT NOT NULL COMMENT 'FK faculties.id',
  `code` VARCHAR(20) NOT NULL COMMENT 'Mã bộ môn trong khoa',
  `name` VARCHAR(150) NOT NULL COMMENT 'Tên bộ môn',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT 'Mặc định 1; chỉ 0 hoặc 1',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_departments_01` UNIQUE (`faculty_id`, `code`),
  CONSTRAINT `uq_departments_02` UNIQUE (`id`, `faculty_id`),
  CONSTRAINT `ck_departments_01` CHECK (is_active IN (0,1))
) ENGINE=InnoDB COMMENT='Bộ môn';


-- 03. Tài khoản người dùng
CREATE TABLE `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `email` VARCHAR(254) COLLATE utf8mb4_0900_as_ci NOT NULL COMMENT 'Email chuẩn hóa chữ thường, UQ',
  `password_hash` VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Giá trị băm mật khẩu, không lưu bản rõ',
  `full_name` VARCHAR(150) NOT NULL COMMENT 'Họ tên',
  `phone` VARCHAR(20) NULL DEFAULT NULL COMMENT 'Số điện thoại',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, LOCKED, DISABLED; mặc định ACTIVE',
  `last_login_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Lần đăng nhập gần nhất',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Thời gian tạo UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT 'Lần cập nhật UTC',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0; tăng khi cập nhật',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_users_01` UNIQUE (`email`),
  CONSTRAINT `ck_users_01` CHECK (CHAR_LENGTH(TRIM(email)) > 0),
  CONSTRAINT `ck_users_02` CHECK (CAST(email AS BINARY) = CAST(LOWER(TRIM(email)) AS BINARY)),
  CONSTRAINT `ck_users_03` CHECK (`status` IN ('ACTIVE', 'LOCKED', 'DISABLED')),
  CONSTRAINT `ck_users_04` CHECK (version >= 0)
) ENGINE=InnoDB COMMENT='Tài khoản người dùng';


-- 04. Vai trò hệ thống
CREATE TABLE `roles` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `code` VARCHAR(20) NOT NULL COMMENT 'STUDENT, LECTURER, ADMIN',
  `name` VARCHAR(100) NOT NULL COMMENT 'Tên hiển thị',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_roles_01` UNIQUE (`code`),
  CONSTRAINT `ck_roles_01` CHECK (`code` IN ('STUDENT', 'LECTURER', 'ADMIN'))
) ENGINE=InnoDB COMMENT='Vai trò hệ thống';


-- 05. Gán vai trò
CREATE TABLE `user_roles` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `user_id` BIGINT NOT NULL COMMENT 'FK users.id',
  `role_id` BIGINT NOT NULL COMMENT 'FK roles.id',
  `assigned_at` DATETIME(6) NOT NULL COMMENT 'Thời điểm cấp quyền',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_user_roles_01` UNIQUE (`user_id`, `role_id`),
  KEY `ix_user_roles_01` (`role_id`)
) ENGINE=InnoDB COMMENT='Gán vai trò';


-- 06. Hồ sơ sinh viên
CREATE TABLE `students` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `user_id` BIGINT NOT NULL COMMENT 'FK users.id; UQ',
  `department_id` BIGINT NOT NULL COMMENT 'FK departments.id',
  `student_code` VARCHAR(20) NOT NULL COMMENT 'MSSV, UQ',
  `class_name` VARCHAR(50) NOT NULL COMMENT 'Lớp học',
  `cohort` VARCHAR(20) NOT NULL COMMENT 'Khóa tuyển sinh',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_students_01` UNIQUE (`user_id`),
  CONSTRAINT `uq_students_02` UNIQUE (`student_code`),
  KEY `ix_students_01` (`department_id`)
) ENGINE=InnoDB COMMENT='Hồ sơ sinh viên';


-- 07. Hồ sơ giảng viên
CREATE TABLE `lecturers` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `user_id` BIGINT NOT NULL COMMENT 'FK users.id; UQ',
  `department_id` BIGINT NOT NULL COMMENT 'FK departments.id',
  `lecturer_code` VARCHAR(20) NOT NULL COMMENT 'Mã giảng viên, UQ',
  `academic_degree` VARCHAR(50) NULL DEFAULT NULL COMMENT 'Học vị',
  `specialization` TEXT NULL DEFAULT NULL COMMENT 'Chuyên môn',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_lecturers_01` UNIQUE (`user_id`),
  CONSTRAINT `uq_lecturers_02` UNIQUE (`lecturer_code`),
  KEY `ix_lecturers_01` (`department_id`)
) ENGINE=InnoDB COMMENT='Hồ sơ giảng viên';


-- 08. Nhiệm vụ quản lý học thuật
CREATE TABLE `academic_appointments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `lecturer_id` BIGINT NOT NULL COMMENT 'FK lecturers.id',
  `faculty_id` BIGINT NOT NULL COMMENT 'FK faculties.id',
  `department_id` BIGINT NULL DEFAULT NULL COMMENT 'FK departments.id; chỉ có với trưởng bộ môn',
  `position_code` VARCHAR(30) NOT NULL COMMENT 'DEAN, DEPARTMENT_HEAD',
  `starts_at` DATETIME(6) NOT NULL COMMENT 'Bắt đầu hiệu lực',
  `ends_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Kết thúc; NULL là chưa xác định',
  `assigned_by` BIGINT NOT NULL COMMENT 'FK users.id',
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_academic_appointments_01` CHECK (ends_at IS NULL OR ends_at > starts_at),
  CONSTRAINT `ck_academic_appointments_02` CHECK ((position_code = 'DEAN' AND department_id IS NULL) OR (position_code = 'DEPARTMENT_HEAD' AND department_id IS NOT NULL)),
  CONSTRAINT `ck_academic_appointments_03` CHECK (`position_code` IN ('DEAN', 'DEPARTMENT_HEAD')),
  KEY `ix_academic_appointments_01` (`department_id`, `faculty_id`),
  KEY `ix_academic_appointments_02` (`assigned_by`),
  KEY `ix_academic_appointments_03` (`faculty_id`),
  KEY `ix_academic_appointments_04` (`lecturer_id`)
) ENGINE=InnoDB COMMENT='Nhiệm vụ quản lý học thuật';


-- 09. Học kỳ
CREATE TABLE `semesters` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `academic_year` VARCHAR(20) NOT NULL COMMENT 'Ví dụ 2026–2027',
  `semester_no` TINYINT NOT NULL COMMENT '1, 2 hoặc 3',
  `starts_on` DATE NOT NULL COMMENT 'Ngày bắt đầu',
  `ends_on` DATE NOT NULL COMMENT 'Ngày kết thúc',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_semesters_01` UNIQUE (`academic_year`, `semester_no`),
  CONSTRAINT `ck_semesters_01` CHECK (semester_no BETWEEN 1 AND 3),
  CONSTRAINT `ck_semesters_02` CHECK (starts_on < ends_on)
) ENGINE=InnoDB COMMENT='Học kỳ';


-- 10. Đợt đăng ký
CREATE TABLE `registration_periods` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `faculty_id` BIGINT NOT NULL COMMENT 'FK faculties.id',
  `semester_id` BIGINT NOT NULL COMMENT 'FK semesters.id',
  `name` VARCHAR(200) NOT NULL COMMENT 'Tên đợt',
  `project_type` VARCHAR(20) NOT NULL COMMENT 'COURSE, RESEARCH, SPECIALIZED, GRADUATION',
  `proposal_start_at` DATETIME(6) NOT NULL COMMENT 'Mở đề xuất cho GV và SV theo phương án',
  `proposal_end_at` DATETIME(6) NOT NULL COMMENT 'Đóng đề xuất',
  `registration_start_at` DATETIME(6) NOT NULL COMMENT 'Mở đăng ký của nhóm',
  `registration_end_at` DATETIME(6) NOT NULL COMMENT 'Đóng đăng ký',
  `review_deadline_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Hạn GVPB nộp điểm cho TLCN/KLTN',
  `defense_date` DATE NULL DEFAULT NULL COMMENT 'Ngày bảo vệ dự kiến cho KLTN',
  `min_group_members` TINYINT NOT NULL DEFAULT 1 COMMENT 'Số thành viên tối thiểu để nhóm hoạt động',
  `max_group_members` TINYINT NOT NULL DEFAULT 3 COMMENT 'Số thành viên tối đa của nhóm',
  `invitation_expiry_hours` SMALLINT NOT NULL DEFAULT 72 COMMENT 'Số giờ tối đa một lời mời nhóm còn hiệu lực',
  `min_committee_members` TINYINT NOT NULL DEFAULT 3 COMMENT 'Số thành viên tối thiểu của hội đồng',
  `max_committee_members` TINYINT NOT NULL DEFAULT 5 COMMENT 'Số thành viên tối đa của hội đồng',
  `status` VARCHAR(20) NOT NULL COMMENT 'DRAFT, ACTIVE, CLOSED, CANCELLED',
  `created_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày tạo',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_registration_periods_01` UNIQUE (`id`, `faculty_id`),
  CONSTRAINT `ck_registration_periods_01` CHECK (proposal_start_at < proposal_end_at AND proposal_end_at <= registration_start_at AND registration_start_at < registration_end_at),
  CONSTRAINT `ck_registration_periods_02` CHECK (project_type IN ('SPECIALIZED','GRADUATION') OR review_deadline_at IS NULL),
  CONSTRAINT `ck_registration_periods_03` CHECK (project_type = 'GRADUATION' OR defense_date IS NULL),
  CONSTRAINT `ck_registration_periods_04` CHECK (status IN ('DRAFT','CANCELLED') OR project_type NOT IN ('SPECIALIZED','GRADUATION') OR review_deadline_at IS NOT NULL),
  CONSTRAINT `ck_registration_periods_05` CHECK (status IN ('DRAFT','CANCELLED') OR project_type <> 'GRADUATION' OR defense_date IS NOT NULL),
  CONSTRAINT `ck_registration_periods_06` CHECK (review_deadline_at IS NULL OR review_deadline_at > registration_end_at),
  CONSTRAINT `ck_registration_periods_07` CHECK (`project_type` IN ('COURSE', 'RESEARCH', 'SPECIALIZED', 'GRADUATION')),
  CONSTRAINT `ck_registration_periods_08` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'CLOSED', 'CANCELLED')),
  CONSTRAINT `ck_registration_periods_09` CHECK (version >= 0),
  CONSTRAINT `ck_registration_periods_10` CHECK (min_group_members BETWEEN 1 AND 10 AND max_group_members BETWEEN min_group_members AND 10),
  CONSTRAINT `ck_registration_periods_11` CHECK (invitation_expiry_hours BETWEEN 1 AND 720),
  CONSTRAINT `ck_registration_periods_12` CHECK (min_committee_members BETWEEN 1 AND 10 AND max_committee_members BETWEEN min_committee_members AND 10),
  KEY `ix_registration_periods_01` (`created_by`),
  KEY `ix_registration_periods_02` (`faculty_id`),
  KEY `ix_registration_periods_03` (`semester_id`)
) ENGINE=InnoDB COMMENT='Đợt đăng ký';


-- 11. Nhóm sinh viên
CREATE TABLE `student_groups` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `name` VARCHAR(150) NOT NULL COMMENT 'Tên nhóm',
  `status` VARCHAR(20) NOT NULL COMMENT 'FORMING, ACTIVE, LOCKED, CLOSED',
  `created_by` BIGINT NOT NULL COMMENT 'FK students.id',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày tạo',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_student_groups_01` UNIQUE (`id`, `period_id`),
  CONSTRAINT `ck_student_groups_01` CHECK (`status` IN ('FORMING', 'ACTIVE', 'LOCKED', 'CLOSED')),
  CONSTRAINT `ck_student_groups_02` CHECK (version >= 0),
  KEY `ix_student_groups_01` (`created_by`),
  KEY `ix_student_groups_02` (`period_id`)
) ENGINE=InnoDB COMMENT='Nhóm sinh viên';


-- 12. Đề tài
CREATE TABLE `topics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `faculty_id` BIGINT NOT NULL COMMENT 'Phạm vi dùng trong FK ghép',
  `department_id` BIGINT NOT NULL COMMENT 'FK departments.id',
  `code` VARCHAR(30) NOT NULL COMMENT 'Mã đề tài trong đợt',
  `title` VARCHAR(300) NOT NULL COMMENT 'Tên đề tài',
  `description` TEXT NOT NULL COMMENT 'Mô tả nội dung',
  `objectives` TEXT NOT NULL COMMENT 'Mục tiêu',
  `expected_outcomes` TEXT NOT NULL COMMENT 'Sản phẩm đầu ra',
  `prerequisites` TEXT NULL DEFAULT NULL COMMENT 'Kiến thức cần có',
  `proposed_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `proposal_source` VARCHAR(20) NOT NULL COMMENT 'STUDENT hoặc LECTURER',
  `visibility_type` VARCHAR(20) NOT NULL COMMENT 'OPEN hoặc RESERVED',
  `reserved_group_id` BIGINT NULL DEFAULT NULL COMMENT 'FK student_groups.id',
  `status` VARCHAR(30) NOT NULL COMMENT 'DRAFT, PENDING, REVISION_REQUIRED, APPROVED, PUBLISHED, REJECTED, WITHDRAWN',
  `published_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày công bố',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày tạo',
  `updated_at` DATETIME(6) NOT NULL COMMENT 'Ngày cập nhật',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_topics_01` UNIQUE (`period_id`, `code`),
  CONSTRAINT `uq_topics_02` UNIQUE (`id`, `period_id`),
  CONSTRAINT `ck_topics_01` CHECK (visibility_type <> 'OPEN' OR reserved_group_id IS NULL),
  CONSTRAINT `ck_topics_02` CHECK (status <> 'PUBLISHED' OR visibility_type <> 'RESERVED' OR reserved_group_id IS NOT NULL),
  CONSTRAINT `ck_topics_03` CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
  CONSTRAINT `ck_topics_04` CHECK (`proposal_source` IN ('STUDENT', 'LECTURER')),
  CONSTRAINT `ck_topics_05` CHECK (`visibility_type` IN ('OPEN', 'RESERVED')),
  CONSTRAINT `ck_topics_06` CHECK (`status` IN ('DRAFT', 'PENDING', 'REVISION_REQUIRED', 'APPROVED', 'PUBLISHED', 'REJECTED', 'WITHDRAWN')),
  CONSTRAINT `ck_topics_07` CHECK (version >= 0),
  KEY `ix_topics_01` (`department_id`, `faculty_id`),
  KEY `ix_topics_02` (`period_id`, `faculty_id`),
  KEY `ix_topics_03` (`period_id`, `status`),
  KEY `ix_topics_04` (`reserved_group_id`, `period_id`),
  KEY `ix_topics_05` (`proposed_by`)
) ENGINE=InnoDB COMMENT='Đề tài';


-- 13. Phân công hướng dẫn
CREATE TABLE `topic_supervisors` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `topic_id` BIGINT NOT NULL COMMENT 'FK topics.id',
  `lecturer_id` BIGINT NOT NULL COMMENT 'FK lecturers.id',
  `slot_no` TINYINT NOT NULL COMMENT '1 là GVHD chính; 2 là GVHD phụ',
  `assigned_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `assigned_at` DATETIME(6) NOT NULL COMMENT 'Ngày phân công',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_topic_supervisors_01` UNIQUE (`topic_id`, `slot_no`),
  CONSTRAINT `uq_topic_supervisors_02` UNIQUE (`topic_id`, `lecturer_id`),
  CONSTRAINT `ck_topic_supervisors_01` CHECK (slot_no BETWEEN 1 AND 2),
  KEY `ix_topic_supervisors_01` (`assigned_by`),
  KEY `ix_topic_supervisors_02` (`lecturer_id`)
) ENGINE=InnoDB COMMENT='Phân công hướng dẫn';


-- 14. Lịch sử duyệt đề tài
CREATE TABLE `topic_reviews` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `topic_id` BIGINT NOT NULL COMMENT 'FK topics.id',
  `reviewed_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `decision` VARCHAR(30) NOT NULL COMMENT 'APPROVED, REVISION_REQUIRED, REJECTED',
  `comment` TEXT NOT NULL COMMENT 'Lý do hoặc nhận xét',
  `reviewed_at` DATETIME(6) NOT NULL COMMENT 'Ngày duyệt',
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_topic_reviews_01` CHECK (`decision` IN ('APPROVED', 'REVISION_REQUIRED', 'REJECTED')),
  KEY `ix_topic_reviews_01` (`reviewed_by`),
  KEY `ix_topic_reviews_02` (`topic_id`)
) ENGINE=InnoDB COMMENT='Lịch sử duyệt đề tài';


-- 15. Lịch sử thành viên nhóm
CREATE TABLE `group_members` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `group_id` BIGINT NOT NULL COMMENT 'FK student_groups.id',
  `period_id` BIGINT NOT NULL COMMENT 'Đợt của nhóm',
  `student_id` BIGINT NOT NULL COMMENT 'FK students.id',
  `slot_no` TINYINT NOT NULL COMMENT 'Vị trí 1–10 của thành viên; giới hạn thực tế do đợt đăng ký cấu hình',
  `member_role` VARCHAR(20) NOT NULL COMMENT 'LEADER hoặc MEMBER',
  `joined_at` DATETIME(6) NOT NULL COMMENT 'Ngày vào nhóm',
  `left_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'NULL khi còn trong nhóm',
  `active_student_id` BIGINT GENERATED ALWAYS AS (CASE WHEN left_at IS NULL THEN student_id ELSE NULL END) STORED COMMENT 'student_id khi left_at NULL; ngược lại NULL',
  `active_slot_no` TINYINT GENERATED ALWAYS AS (CASE WHEN left_at IS NULL THEN slot_no ELSE NULL END) STORED COMMENT 'slot_no khi left_at NULL; ngược lại NULL',
  `active_leader_group_id` BIGINT GENERATED ALWAYS AS (CASE WHEN left_at IS NULL AND member_role = 'LEADER' THEN group_id ELSE NULL END) STORED COMMENT 'group_id khi còn hoạt động và LEADER; ngược lại NULL',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_group_members_01` UNIQUE (`period_id`, `active_student_id`),
  CONSTRAINT `uq_group_members_02` UNIQUE (`group_id`, `active_slot_no`),
  CONSTRAINT `uq_group_members_03` UNIQUE (`active_leader_group_id`),
  CONSTRAINT `ck_group_members_01` CHECK (slot_no BETWEEN 1 AND 10),
  CONSTRAINT `ck_group_members_02` CHECK (left_at IS NULL OR left_at >= joined_at),
  CONSTRAINT `ck_group_members_03` CHECK (`member_role` IN ('LEADER', 'MEMBER')),
  KEY `ix_group_members_01` (`group_id`, `left_at`),
  KEY `ix_group_members_02` (`group_id`, `period_id`),
  KEY `ix_group_members_03` (`student_id`)
) ENGINE=InnoDB COMMENT='Lịch sử thành viên nhóm';


-- 16. Lời mời vào nhóm
CREATE TABLE `group_invitations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `group_id` BIGINT NOT NULL COMMENT 'FK student_groups.id',
  `invited_student_id` BIGINT NOT NULL COMMENT 'FK students.id',
  `invited_by` BIGINT NOT NULL COMMENT 'FK students.id',
  `status` VARCHAR(20) NOT NULL COMMENT 'PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày gửi',
  `expires_at` DATETIME(6) NOT NULL COMMENT 'Hạn phản hồi',
  `responded_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày xử lý',
  `pending_student_id` BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN invited_student_id ELSE NULL END) STORED COMMENT 'invited_student_id nếu PENDING; ngược lại NULL',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_group_invitations_01` UNIQUE (`group_id`, `pending_student_id`),
  CONSTRAINT `ck_group_invitations_01` CHECK (expires_at > created_at),
  CONSTRAINT `ck_group_invitations_02` CHECK (responded_at IS NULL OR responded_at >= created_at),
  CONSTRAINT `ck_group_invitations_03` CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
  KEY `ix_group_invitations_01` (`invited_student_id`, `status`),
  KEY `ix_group_invitations_02` (`invited_by`)
) ENGINE=InnoDB COMMENT='Lời mời vào nhóm';


-- 17. Đăng ký đề tài
CREATE TABLE `topic_registrations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `group_id` BIGINT NOT NULL COMMENT 'FK student_groups.id',
  `topic_id` BIGINT NOT NULL COMMENT 'FK topics.id',
  `registered_by` BIGINT NOT NULL COMMENT 'FK students.id',
  `motivation` TEXT NULL DEFAULT NULL COMMENT 'Lý do chọn đề tài',
  `status` VARCHAR(20) NOT NULL COMMENT 'PENDING, APPROVED, REJECTED, WITHDRAWN',
  `registered_at` DATETIME(6) NOT NULL COMMENT 'Ngày đăng ký',
  `reviewed_by` BIGINT NULL DEFAULT NULL COMMENT 'FK users.id',
  `reviewed_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày xét duyệt',
  `decision_reason` TEXT NULL DEFAULT NULL COMMENT 'Kết quả xét duyệt',
  `pending_group_id` BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN group_id ELSE NULL END) STORED COMMENT 'group_id khi PENDING; ngược lại NULL',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_topic_registrations_01` UNIQUE (`pending_group_id`),
  CONSTRAINT `uq_topic_registrations_02` UNIQUE (`id`, `group_id`, `topic_id`, `period_id`),
  CONSTRAINT `ck_topic_registrations_01` CHECK (status NOT IN ('APPROVED','REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CONSTRAINT `ck_topic_registrations_02` CHECK (status NOT IN ('REJECTED','WITHDRAWN') OR (decision_reason IS NOT NULL AND CHAR_LENGTH(TRIM(decision_reason)) > 0)),
  CONSTRAINT `ck_topic_registrations_03` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
  CONSTRAINT `ck_topic_registrations_04` CHECK (version >= 0),
  KEY `ix_topic_registrations_01` (`group_id`, `period_id`),
  KEY `ix_topic_registrations_02` (`topic_id`, `period_id`),
  KEY `ix_topic_registrations_03` (`topic_id`, `status`),
  KEY `ix_topic_registrations_04` (`period_id`),
  KEY `ix_topic_registrations_05` (`registered_by`),
  KEY `ix_topic_registrations_06` (`reviewed_by`)
) ENGINE=InnoDB COMMENT='Đăng ký đề tài';


-- 18. Quyết định giao đề tài
CREATE TABLE `project_assignments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `registration_id` BIGINT NOT NULL COMMENT 'FK topic_registrations.id; UQ',
  `group_id` BIGINT NOT NULL COMMENT 'FK student_groups.id; UQ',
  `topic_id` BIGINT NOT NULL COMMENT 'FK topics.id; UQ',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `assigned_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `assigned_at` DATETIME(6) NOT NULL COMMENT 'Ngày giao',
  `status` VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS, COMPLETED, CANCELLED',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_project_assignments_01` UNIQUE (`registration_id`),
  CONSTRAINT `uq_project_assignments_02` UNIQUE (`group_id`),
  CONSTRAINT `uq_project_assignments_03` UNIQUE (`topic_id`),
  CONSTRAINT `uq_project_assignments_04` UNIQUE (`id`, `period_id`),
  CONSTRAINT `ck_project_assignments_01` CHECK (`status` IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT `ck_project_assignments_02` CHECK (version >= 0),
  KEY `ix_project_assignments_01` (`registration_id`, `group_id`, `topic_id`, `period_id`),
  KEY `ix_project_assignments_02` (`assigned_by`),
  KEY `ix_project_assignments_03` (`period_id`)
) ENGINE=InnoDB COMMENT='Quyết định giao đề tài';


-- 19. Mốc tiến độ
CREATE TABLE `milestones` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `assignment_id` BIGINT NOT NULL COMMENT 'FK project_assignments.id',
  `title` VARCHAR(200) NOT NULL COMMENT 'Tên mốc',
  `milestone_type` VARCHAR(20) NOT NULL COMMENT 'PROPOSAL, PROGRESS, FINAL',
  `description` TEXT NOT NULL COMMENT 'Yêu cầu công việc',
  `due_at` DATETIME(6) NOT NULL COMMENT 'Hạn nộp',
  `status` VARCHAR(30) NOT NULL COMMENT 'OPEN, SUBMITTED, ACCEPTED, REVISION_REQUIRED',
  `created_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày tạo',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_milestones_01` CHECK (`milestone_type` IN ('PROPOSAL', 'PROGRESS', 'FINAL')),
  CONSTRAINT `ck_milestones_02` CHECK (`status` IN ('OPEN', 'SUBMITTED', 'ACCEPTED', 'REVISION_REQUIRED')),
  CONSTRAINT `ck_milestones_03` CHECK (version >= 0),
  KEY `ix_milestones_01` (`assignment_id`, `due_at`),
  KEY `ix_milestones_02` (`created_by`)
) ENGINE=InnoDB COMMENT='Mốc tiến độ';


-- 20. Thông tin tệp
CREATE TABLE `stored_files` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `storage_key` VARCHAR(500) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Khóa lưu tệp riêng, UQ',
  `original_name` VARCHAR(255) NOT NULL COMMENT 'Tên gốc',
  `mime_type` VARCHAR(100) NOT NULL COMMENT 'Loại tệp đã kiểm tra',
  `size_bytes` BIGINT NOT NULL COMMENT 'Dung lượng, lớn hơn 0',
  `sha256` CHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Giá trị kiểm tra toàn vẹn',
  `uploaded_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `uploaded_at` DATETIME(6) NOT NULL COMMENT 'Ngày tải lên',
  `status` VARCHAR(20) NOT NULL COMMENT 'STAGED, READY, DELETED',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_stored_files_01` UNIQUE (`storage_key`),
  CONSTRAINT `ck_stored_files_01` CHECK (size_bytes > 0),
  CONSTRAINT `ck_stored_files_02` CHECK (CHAR_LENGTH(sha256) = 64),
  CONSTRAINT `ck_stored_files_03` CHECK (`status` IN ('STAGED', 'READY', 'DELETED')),
  KEY `ix_stored_files_01` (`uploaded_by`)
) ENGINE=InnoDB COMMENT='Thông tin tệp';


-- 21. Các phiên bản báo cáo
CREATE TABLE `report_submissions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `milestone_id` BIGINT NOT NULL COMMENT 'FK milestones.id',
  `version_no` INT NOT NULL COMMENT 'Phiên bản, bắt đầu từ 1',
  `file_id` BIGINT NOT NULL COMMENT 'FK stored_files.id',
  `submitted_by` BIGINT NOT NULL COMMENT 'FK students.id',
  `note` TEXT NULL DEFAULT NULL COMMENT 'Ghi chú',
  `submitted_at` DATETIME(6) NOT NULL COMMENT 'Ngày nộp',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_report_submissions_01` UNIQUE (`milestone_id`, `version_no`),
  CONSTRAINT `ck_report_submissions_01` CHECK (version_no >= 1),
  KEY `ix_report_submissions_01` (`milestone_id`, `submitted_at`),
  KEY `ix_report_submissions_02` (`file_id`),
  KEY `ix_report_submissions_03` (`submitted_by`)
) ENGINE=InnoDB COMMENT='Các phiên bản báo cáo';


-- 22. Phản hồi báo cáo
CREATE TABLE `report_feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `submission_id` BIGINT NOT NULL COMMENT 'FK report_submissions.id',
  `lecturer_id` BIGINT NOT NULL COMMENT 'FK lecturers.id',
  `content` TEXT NOT NULL COMMENT 'Nhận xét',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày phản hồi',
  PRIMARY KEY (`id`),
  KEY `ix_report_feedback_01` (`lecturer_id`),
  KEY `ix_report_feedback_02` (`submission_id`)
) ENGINE=InnoDB COMMENT='Phản hồi báo cáo';


-- 23. Hội đồng đánh giá
CREATE TABLE `evaluation_committees` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `faculty_id` BIGINT NOT NULL COMMENT 'FK faculties.id',
  `rubric_id` BIGINT NULL COMMENT 'Bộ tiêu chí được chọn cho hội đồng',
  `name` VARCHAR(200) NOT NULL COMMENT 'Tên hội đồng',
  `status` VARCHAR(20) NOT NULL COMMENT 'DRAFT, CONFIRMED, COMPLETED',
  `created_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày tạo',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_evaluation_committees_01` UNIQUE (`id`, `period_id`),
  CONSTRAINT `ck_evaluation_committees_01` CHECK (`status` IN ('DRAFT', 'CONFIRMED', 'COMPLETED')),
  CONSTRAINT `ck_evaluation_committees_02` CHECK (version >= 0),
  KEY `ix_evaluation_committees_01` (`period_id`, `faculty_id`),
  KEY `ix_evaluation_committees_02` (`created_by`),
  KEY `ix_evaluation_committees_03` (`faculty_id`)
) ENGINE=InnoDB COMMENT='Hội đồng đánh giá';


-- 24. Thành viên hội đồng
CREATE TABLE `committee_members` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `committee_id` BIGINT NOT NULL COMMENT 'FK evaluation_committees.id',
  `lecturer_id` BIGINT NOT NULL COMMENT 'FK lecturers.id',
  `slot_no` TINYINT NOT NULL COMMENT 'Vị trí 1–10; giới hạn thực tế do đợt đăng ký cấu hình',
  `role_label` VARCHAR(100) NULL COMMENT 'Vai trò do người dùng nhập',
  `committee_role` VARCHAR(20) NOT NULL COMMENT 'CHAIR, SECRETARY, MEMBER',
  `unique_office` VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN committee_role IN ('CHAIR','SECRETARY') THEN committee_role ELSE NULL END) STORED COMMENT 'CHAIR hoặc SECRETARY; MEMBER cho NULL',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_committee_members_01` UNIQUE (`committee_id`, `slot_no`),
  CONSTRAINT `uq_committee_members_02` UNIQUE (`committee_id`, `lecturer_id`),
  CONSTRAINT `uq_committee_members_03` UNIQUE (`committee_id`, `unique_office`),
  CONSTRAINT `ck_committee_members_01` CHECK (slot_no BETWEEN 1 AND 10),
  CONSTRAINT `ck_committee_members_02` CHECK (`committee_role` IN ('CHAIR', 'SECRETARY', 'MEMBER')),
  KEY `ix_committee_members_01` (`lecturer_id`)
) ENGINE=InnoDB COMMENT='Thành viên hội đồng';


-- 25. Lịch bảo vệ
CREATE TABLE `defense_sessions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `assignment_id` BIGINT NOT NULL COMMENT 'FK project_assignments.id; UQ',
  `committee_id` BIGINT NOT NULL COMMENT 'FK evaluation_committees.id',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `location` VARCHAR(200) NOT NULL COMMENT 'Địa điểm hoặc phòng bảo vệ dạng mô tả',
  `starts_at` DATETIME(6) NOT NULL COMMENT 'Bắt đầu UTC',
  `ends_at` DATETIME(6) NOT NULL COMMENT 'Kết thúc UTC',
  `status` VARCHAR(20) NOT NULL COMMENT 'SCHEDULED, COMPLETED, CANCELLED',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_defense_sessions_01` UNIQUE (`assignment_id`),
  CONSTRAINT `uq_defense_sessions_02` UNIQUE (`id`, `assignment_id`, `period_id`),
  CONSTRAINT `ck_defense_sessions_01` CHECK (starts_at < ends_at),
  CONSTRAINT `ck_defense_sessions_02` CHECK (`status` IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT `ck_defense_sessions_03` CHECK (version >= 0),
  KEY `ix_defense_sessions_01` (`committee_id`, `starts_at`, `ends_at`),
  KEY `ix_defense_sessions_02` (`assignment_id`, `period_id`),
  KEY `ix_defense_sessions_03` (`committee_id`, `period_id`),
  KEY `ix_defense_sessions_04` (`period_id`)
) ENGINE=InnoDB COMMENT='Lịch bảo vệ';


-- 26. Điểm thành phần của đợt
CREATE TABLE `assessment_components` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `code` VARCHAR(30) NOT NULL COMMENT 'Ví dụ REVIEW, DEFENSE',
  `name` VARCHAR(150) NOT NULL COMMENT 'Tên thành phần',
  `component_type` VARCHAR(20) NOT NULL COMMENT 'REVIEW, COMMITTEE, OTHER',
  `expected_evaluators` TINYINT NOT NULL COMMENT 'Số người chấm cần đủ',
  `status` VARCHAR(20) NOT NULL COMMENT 'DRAFT, LOCKED',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_assessment_components_01` UNIQUE (`period_id`, `code`),
  CONSTRAINT `uq_assessment_components_02` UNIQUE (`id`, `period_id`),
  CONSTRAINT `ck_assessment_components_01` CHECK (expected_evaluators BETWEEN 1 AND 10),
  CONSTRAINT `ck_assessment_components_02` CHECK (component_type <> 'COMMITTEE' OR expected_evaluators BETWEEN 1 AND 10),
  CONSTRAINT `ck_assessment_components_03` CHECK (`component_type` IN ('REVIEW', 'COMMITTEE', 'OTHER')),
  CONSTRAINT `ck_assessment_components_04` CHECK (`status` IN ('DRAFT', 'LOCKED'))
) ENGINE=InnoDB COMMENT='Điểm thành phần của đợt';


-- 27. Bộ tiêu chí chấm
CREATE TABLE `rubrics` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `component_id` BIGINT NOT NULL COMMENT 'FK assessment_components.id',
  `name` VARCHAR(200) NOT NULL COMMENT 'Tên bộ tiêu chí',
  `version_no` INT NOT NULL COMMENT 'Phiên bản từ 1',
  `status` VARCHAR(20) NOT NULL COMMENT 'DRAFT, LOCKED',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày tạo',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_rubrics_01` UNIQUE (`component_id`, `version_no`),
  CONSTRAINT `uq_rubrics_02` UNIQUE (`id`, `component_id`),
  CONSTRAINT `ck_rubrics_01` CHECK (`status` IN ('DRAFT', 'LOCKED')),
  CONSTRAINT `ck_rubrics_02` CHECK (version_no >= 1)
) ENGINE=InnoDB COMMENT='Bộ tiêu chí chấm';


-- 28. Tiêu chí chi tiết
CREATE TABLE `rubric_criteria` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `rubric_id` BIGINT NOT NULL COMMENT 'FK rubrics.id',
  `name` VARCHAR(200) NOT NULL COMMENT 'Tên tiêu chí',
  `description` TEXT NOT NULL COMMENT 'Hướng dẫn chấm',
  `max_score` DECIMAL(5,2) NOT NULL COMMENT 'Điểm tối đa > 0 và ≤ 10',
  `sort_order` INT NOT NULL COMMENT 'Thứ tự hiển thị',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_rubric_criteria_01` UNIQUE (`rubric_id`, `sort_order`),
  CONSTRAINT `uq_rubric_criteria_02` UNIQUE (`id`, `rubric_id`, `max_score`),
  CONSTRAINT `ck_rubric_criteria_01` CHECK (max_score > 0 AND max_score <= 10),
  CONSTRAINT `ck_rubric_criteria_02` CHECK (sort_order >= 1)
) ENGINE=InnoDB COMMENT='Tiêu chí chi tiết';


-- 29. Phân công chấm điểm
CREATE TABLE `evaluation_assignments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `assignment_id` BIGINT NOT NULL COMMENT 'FK project_assignments.id',
  `period_id` BIGINT NOT NULL COMMENT 'FK registration_periods.id',
  `component_id` BIGINT NOT NULL COMMENT 'FK assessment_components.id',
  `rubric_id` BIGINT NOT NULL COMMENT 'FK rubrics.id',
  `lecturer_id` BIGINT NOT NULL COMMENT 'FK lecturers.id',
  `evaluator_role` VARCHAR(20) NOT NULL COMMENT 'REVIEWER, COMMITTEE_MEMBER hoặc OTHER',
  `defense_id` BIGINT NULL DEFAULT NULL COMMENT 'FK defense_sessions.id',
  `status` VARCHAR(20) NOT NULL COMMENT 'ACTIVE, CANCELLED',
  `deadline_at` DATETIME(6) NOT NULL COMMENT 'Hạn chấm',
  `active_lecturer_id` BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN lecturer_id ELSE NULL END) STORED COMMENT 'lecturer_id khi ACTIVE; ngược lại NULL',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_evaluation_assignments_01` UNIQUE (`assignment_id`, `component_id`, `active_lecturer_id`),
  CONSTRAINT `uq_evaluation_assignments_02` UNIQUE (`id`, `rubric_id`),
  CONSTRAINT `ck_evaluation_assignments_01` CHECK (`evaluator_role` IN ('REVIEWER', 'COMMITTEE_MEMBER', 'OTHER')),
  CONSTRAINT `ck_evaluation_assignments_02` CHECK (`status` IN ('ACTIVE', 'CANCELLED')),
  KEY `ix_evaluation_assignments_01` (`defense_id`, `assignment_id`, `period_id`),
  KEY `ix_evaluation_assignments_02` (`lecturer_id`, `status`, `deadline_at`),
  KEY `ix_evaluation_assignments_03` (`assignment_id`, `period_id`),
  KEY `ix_evaluation_assignments_04` (`component_id`, `period_id`),
  KEY `ix_evaluation_assignments_05` (`rubric_id`, `component_id`),
  KEY `ix_evaluation_assignments_06` (`period_id`)
) ENGINE=InnoDB COMMENT='Phân công chấm điểm';


-- 30. Phiếu chấm
CREATE TABLE `score_sheets` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `evaluation_id` BIGINT NOT NULL COMMENT 'FK evaluation_assignments.id; UQ',
  `rubric_id` BIGINT NOT NULL COMMENT 'FK rubrics.id',
  `overall_comment` TEXT NULL DEFAULT NULL COMMENT 'Nhận xét chung',
  `status` VARCHAR(20) NOT NULL COMMENT 'DRAFT, SUBMITTED, LOCKED',
  `submitted_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày nộp',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Mặc định 0',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_score_sheets_01` UNIQUE (`evaluation_id`),
  CONSTRAINT `uq_score_sheets_02` UNIQUE (`id`, `rubric_id`),
  CONSTRAINT `ck_score_sheets_01` CHECK (status = 'DRAFT' OR submitted_at IS NOT NULL),
  CONSTRAINT `ck_score_sheets_02` CHECK (`status` IN ('DRAFT', 'SUBMITTED', 'LOCKED')),
  CONSTRAINT `ck_score_sheets_03` CHECK (version >= 0),
  KEY `ix_score_sheets_01` (`evaluation_id`, `rubric_id`),
  KEY `ix_score_sheets_02` (`rubric_id`)
) ENGINE=InnoDB COMMENT='Phiếu chấm';


-- 31. Điểm tiêu chí
CREATE TABLE `score_details` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `sheet_id` BIGINT NOT NULL COMMENT 'FK score_sheets.id',
  `rubric_id` BIGINT NOT NULL COMMENT 'Rubric dùng để kiểm tra FK ghép',
  `criterion_id` BIGINT NOT NULL COMMENT 'FK rubric_criteria.id',
  `max_score` DECIMAL(5,2) NOT NULL COMMENT 'Bản sao có ràng buộc với tiêu chí',
  `score` DECIMAL(5,2) NOT NULL COMMENT 'Điểm thực tế',
  `comment` TEXT NULL DEFAULT NULL COMMENT 'Nhận xét tiêu chí',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_score_details_01` UNIQUE (`sheet_id`, `criterion_id`),
  CONSTRAINT `ck_score_details_01` CHECK (max_score > 0 AND max_score <= 10),
  CONSTRAINT `ck_score_details_02` CHECK (score >= 0 AND score <= max_score),
  KEY `ix_score_details_01` (`criterion_id`, `rubric_id`, `max_score`),
  KEY `ix_score_details_02` (`sheet_id`, `rubric_id`)
) ENGINE=InnoDB COMMENT='Điểm tiêu chí';


-- 32. Phiên bản kết quả công bố
CREATE TABLE `result_versions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `assignment_id` BIGINT NOT NULL COMMENT 'FK project_assignments.id',
  `version_no` INT NOT NULL COMMENT 'Phiên bản từ 1',
  `final_score` DECIMAL(5,2) NOT NULL COMMENT 'Điểm cuối 0..10',
  `calculation_rule` VARCHAR(100) NOT NULL COMMENT 'MEAN_COMPONENTS_V1',
  `status` VARCHAR(20) NOT NULL COMMENT 'FINALIZED, PUBLISHED, SUPERSEDED',
  `finalized_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `finalized_at` DATETIME(6) NOT NULL COMMENT 'Ngày chốt',
  `published_by` BIGINT NULL DEFAULT NULL COMMENT 'FK users.id',
  `published_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày công bố',
  `current_assignment_id` BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PUBLISHED' THEN assignment_id ELSE NULL END) STORED COMMENT 'assignment_id khi PUBLISHED; ngược lại NULL',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_result_versions_01` UNIQUE (`assignment_id`, `version_no`),
  CONSTRAINT `uq_result_versions_02` UNIQUE (`current_assignment_id`),
  CONSTRAINT `ck_result_versions_01` CHECK (final_score BETWEEN 0 AND 10),
  CONSTRAINT `ck_result_versions_02` CHECK ((status = 'FINALIZED' AND published_by IS NULL AND published_at IS NULL) OR (status IN ('PUBLISHED','SUPERSEDED') AND published_by IS NOT NULL AND published_at IS NOT NULL)),
  CONSTRAINT `ck_result_versions_03` CHECK (`status` IN ('FINALIZED', 'PUBLISHED', 'SUPERSEDED')),
  CONSTRAINT `ck_result_versions_04` CHECK (`calculation_rule` IN ('MEAN_COMPONENTS_V1')),
  CONSTRAINT `ck_result_versions_05` CHECK (version_no >= 1),
  KEY `ix_result_versions_01` (`finalized_by`),
  KEY `ix_result_versions_02` (`published_by`)
) ENGINE=InnoDB COMMENT='Phiên bản kết quả công bố';


-- 33. Nguồn điểm của phiên bản kết quả
CREATE TABLE `result_inputs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `result_id` BIGINT NOT NULL COMMENT 'FK result_versions.id',
  `sheet_id` BIGINT NOT NULL COMMENT 'FK score_sheets.id',
  `component_id` BIGINT NOT NULL COMMENT 'FK assessment_components.id',
  `sheet_version` INT NOT NULL COMMENT 'Phiên bản phiếu tại lúc chốt',
  `score_snapshot` DECIMAL(5,2) NOT NULL COMMENT 'Tổng điểm phiếu tại lúc chốt',
  `rubric_snapshot` JSON NOT NULL COMMENT 'Tên tiêu chí, điểm tối đa, điểm thực tế, nhận xét',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_result_inputs_01` UNIQUE (`result_id`, `sheet_id`),
  CONSTRAINT `ck_result_inputs_01` CHECK (score_snapshot BETWEEN 0 AND 10),
  CONSTRAINT `ck_result_inputs_02` CHECK (sheet_version >= 0),
  KEY `ix_result_inputs_01` (`component_id`),
  KEY `ix_result_inputs_02` (`sheet_id`)
) ENGINE=InnoDB COMMENT='Nguồn điểm của phiên bản kết quả';


-- 34. Danh sách sinh viên nhận kết quả
CREATE TABLE `result_members` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `result_id` BIGINT NOT NULL COMMENT 'FK result_versions.id',
  `student_id` BIGINT NOT NULL COMMENT 'FK students.id',
  `student_code_snapshot` VARCHAR(20) NOT NULL COMMENT 'MSSV khi chốt',
  `full_name_snapshot` VARCHAR(150) NOT NULL COMMENT 'Họ tên khi chốt',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_result_members_01` UNIQUE (`result_id`, `student_id`),
  KEY `ix_result_members_01` (`student_id`)
) ENGINE=InnoDB COMMENT='Danh sách sinh viên nhận kết quả';


-- 35. Yêu cầu mở lại điểm
CREATE TABLE `grade_change_requests` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `sheet_id` BIGINT NOT NULL COMMENT 'FK score_sheets.id',
  `requested_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `reason` TEXT NOT NULL COMMENT 'Lý do mở lại',
  `status` VARCHAR(20) NOT NULL COMMENT 'PENDING, APPROVED, REJECTED, APPLIED',
  `reviewed_by` BIGINT NULL DEFAULT NULL COMMENT 'FK users.id',
  `review_comment` TEXT NULL DEFAULT NULL COMMENT 'Lý do quyết định',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày yêu cầu',
  `reviewed_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày duyệt',
  `applied_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày áp dụng',
  `pending_sheet_id` BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','APPROVED') THEN sheet_id ELSE NULL END) STORED COMMENT 'sheet_id khi PENDING hoặc APPROVED; ngược lại NULL',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_grade_change_requests_01` UNIQUE (`pending_sheet_id`),
  CONSTRAINT `ck_grade_change_requests_01` CHECK (status NOT IN ('APPROVED','REJECTED','APPLIED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CONSTRAINT `ck_grade_change_requests_02` CHECK (status <> 'APPLIED' OR applied_at IS NOT NULL),
  CONSTRAINT `ck_grade_change_requests_03` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
  KEY `ix_grade_change_requests_01` (`requested_by`),
  KEY `ix_grade_change_requests_02` (`reviewed_by`),
  KEY `ix_grade_change_requests_03` (`sheet_id`)
) ENGINE=InnoDB COMMENT='Yêu cầu mở lại điểm';


-- 36. Thông báo chính thức
CREATE TABLE `announcements` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `faculty_id` BIGINT NOT NULL COMMENT 'FK faculties.id',
  `title` VARCHAR(250) NOT NULL COMMENT 'Tiêu đề',
  `content` TEXT NOT NULL COMMENT 'Nội dung',
  `file_id` BIGINT NULL DEFAULT NULL COMMENT 'FK stored_files.id',
  `created_by` BIGINT NOT NULL COMMENT 'FK users.id',
  `status` VARCHAR(20) NOT NULL COMMENT 'DRAFT, PUBLISHED, ARCHIVED',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày tạo',
  `published_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'Ngày công bố',
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_announcements_01` CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
  CONSTRAINT `ck_announcements_02` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  KEY `ix_announcements_01` (`created_by`),
  KEY `ix_announcements_02` (`faculty_id`),
  KEY `ix_announcements_03` (`file_id`)
) ENGINE=InnoDB COMMENT='Thông báo chính thức';


-- 37. Thông báo cá nhân
CREATE TABLE `notifications` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `user_id` BIGINT NOT NULL COMMENT 'FK users.id',
  `title` VARCHAR(250) NOT NULL COMMENT 'Tiêu đề',
  `content` TEXT NOT NULL COMMENT 'Nội dung',
  `target_path` VARCHAR(500) NULL DEFAULT NULL COMMENT 'Đường dẫn nội bộ',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày gửi',
  `read_at` DATETIME(6) NULL DEFAULT NULL COMMENT 'NULL nghĩa là chưa đọc',
  `event_key` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Định danh sự kiện chống gửi trùng',
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_notifications_01` UNIQUE (`user_id`, `event_key`),
  CONSTRAINT `ck_notifications_01` CHECK (read_at IS NULL OR read_at >= created_at),
  KEY `ix_notifications_01` (`user_id`, `read_at`, `created_at`)
) ENGINE=InnoDB COMMENT='Thông báo cá nhân';


-- 38. Nhật ký nghiệp vụ và AOP
CREATE TABLE `audit_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK; tự tăng; số dương',
  `actor_id` BIGINT NULL DEFAULT NULL COMMENT 'FK users.id; NULL cho tác vụ hệ thống',
  `action` VARCHAR(100) NOT NULL COMMENT 'Tên thao tác',
  `entity_type` VARCHAR(100) NOT NULL COMMENT 'Loại đối tượng',
  `entity_id` BIGINT NULL DEFAULT NULL COMMENT 'ID tham chiếu tổng quát, không phải FK đa bảng',
  `old_values` JSON NULL DEFAULT NULL COMMENT 'Dữ liệu trước đã lọc bí mật',
  `new_values` JSON NULL DEFAULT NULL COMMENT 'Dữ liệu sau đã lọc bí mật',
  `outcome` VARCHAR(20) NOT NULL COMMENT 'SUCCESS, FAILURE',
  `request_id` VARCHAR(100) NOT NULL COMMENT 'Mã đối chiếu yêu cầu',
  `duration_ms` BIGINT NULL DEFAULT NULL COMMENT 'Thời gian xử lý không âm',
  `created_at` DATETIME(6) NOT NULL COMMENT 'Ngày ghi',
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_audit_logs_01` CHECK (duration_ms IS NULL OR duration_ms >= 0),
  CONSTRAINT `ck_audit_logs_02` CHECK (`outcome` IN ('SUCCESS', 'FAILURE')),
  KEY `ix_audit_logs_01` (`entity_type`, `entity_id`, `created_at`),
  KEY `ix_audit_logs_02` (`actor_id`)
) ENGINE=InnoDB COMMENT='Nhật ký nghiệp vụ và AOP';


-- PHẦN 2: KHÓA NGOẠI (thêm sau khi mọi bảng và UNIQUE đã tồn tại)

ALTER TABLE `departments` ADD CONSTRAINT `fk_departments_01` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_user_roles_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_user_roles_02` FOREIGN KEY (`role_id`)
  REFERENCES `roles` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `students` ADD CONSTRAINT `fk_students_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `students` ADD CONSTRAINT `fk_students_02` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `lecturers` ADD CONSTRAINT `fk_lecturers_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `lecturers` ADD CONSTRAINT `fk_lecturers_02` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_01` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_02` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_03` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_04` FOREIGN KEY (`assigned_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_05` FOREIGN KEY (`department_id`, `faculty_id`)
  REFERENCES `departments` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `registration_periods` ADD CONSTRAINT `fk_registration_periods_01` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `registration_periods` ADD CONSTRAINT `fk_registration_periods_02` FOREIGN KEY (`semester_id`)
  REFERENCES `semesters` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `registration_periods` ADD CONSTRAINT `fk_registration_periods_03` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `student_groups` ADD CONSTRAINT `fk_student_groups_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `student_groups` ADD CONSTRAINT `fk_student_groups_02` FOREIGN KEY (`created_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_02` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_03` FOREIGN KEY (`proposed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_04` FOREIGN KEY (`reserved_group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_05` FOREIGN KEY (`period_id`, `faculty_id`)
  REFERENCES `registration_periods` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_06` FOREIGN KEY (`department_id`, `faculty_id`)
  REFERENCES `departments` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_07` FOREIGN KEY (`reserved_group_id`, `period_id`)
  REFERENCES `student_groups` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_supervisors` ADD CONSTRAINT `fk_topic_supervisors_01` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_supervisors` ADD CONSTRAINT `fk_topic_supervisors_02` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_supervisors` ADD CONSTRAINT `fk_topic_supervisors_03` FOREIGN KEY (`assigned_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_reviews` ADD CONSTRAINT `fk_topic_reviews_01` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_reviews` ADD CONSTRAINT `fk_topic_reviews_02` FOREIGN KEY (`reviewed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_members` ADD CONSTRAINT `fk_group_members_01` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_members` ADD CONSTRAINT `fk_group_members_02` FOREIGN KEY (`student_id`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_members` ADD CONSTRAINT `fk_group_members_03` FOREIGN KEY (`group_id`, `period_id`)
  REFERENCES `student_groups` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_invitations` ADD CONSTRAINT `fk_group_invitations_01` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_invitations` ADD CONSTRAINT `fk_group_invitations_02` FOREIGN KEY (`invited_student_id`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_invitations` ADD CONSTRAINT `fk_group_invitations_03` FOREIGN KEY (`invited_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_02` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_03` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_04` FOREIGN KEY (`registered_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_05` FOREIGN KEY (`reviewed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_06` FOREIGN KEY (`group_id`, `period_id`)
  REFERENCES `student_groups` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_07` FOREIGN KEY (`topic_id`, `period_id`)
  REFERENCES `topics` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_01` FOREIGN KEY (`registration_id`)
  REFERENCES `topic_registrations` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_02` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_03` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_04` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_05` FOREIGN KEY (`assigned_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_06` FOREIGN KEY (`registration_id`, `group_id`, `topic_id`, `period_id`)
  REFERENCES `topic_registrations` (`id`, `group_id`, `topic_id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `milestones` ADD CONSTRAINT `fk_milestones_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `milestones` ADD CONSTRAINT `fk_milestones_02` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `stored_files` ADD CONSTRAINT `fk_stored_files_01` FOREIGN KEY (`uploaded_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_submissions` ADD CONSTRAINT `fk_report_submissions_01` FOREIGN KEY (`milestone_id`)
  REFERENCES `milestones` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_submissions` ADD CONSTRAINT `fk_report_submissions_02` FOREIGN KEY (`file_id`)
  REFERENCES `stored_files` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_submissions` ADD CONSTRAINT `fk_report_submissions_03` FOREIGN KEY (`submitted_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_feedback` ADD CONSTRAINT `fk_report_feedback_01` FOREIGN KEY (`submission_id`)
  REFERENCES `report_submissions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_feedback` ADD CONSTRAINT `fk_report_feedback_02` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_02` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_03` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_04` FOREIGN KEY (`period_id`, `faculty_id`)
  REFERENCES `registration_periods` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_rubric` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `committee_members` ADD CONSTRAINT `fk_committee_members_01` FOREIGN KEY (`committee_id`)
  REFERENCES `evaluation_committees` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `committee_members` ADD CONSTRAINT `fk_committee_members_02` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_02` FOREIGN KEY (`committee_id`)
  REFERENCES `evaluation_committees` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_03` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_04` FOREIGN KEY (`assignment_id`, `period_id`)
  REFERENCES `project_assignments` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_05` FOREIGN KEY (`committee_id`, `period_id`)
  REFERENCES `evaluation_committees` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `assessment_components` ADD CONSTRAINT `fk_assessment_components_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `rubrics` ADD CONSTRAINT `fk_rubrics_01` FOREIGN KEY (`component_id`)
  REFERENCES `assessment_components` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `rubric_criteria` ADD CONSTRAINT `fk_rubric_criteria_01` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_02` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_03` FOREIGN KEY (`component_id`)
  REFERENCES `assessment_components` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_04` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_05` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_06` FOREIGN KEY (`defense_id`)
  REFERENCES `defense_sessions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_07` FOREIGN KEY (`assignment_id`, `period_id`)
  REFERENCES `project_assignments` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_08` FOREIGN KEY (`component_id`, `period_id`)
  REFERENCES `assessment_components` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_09` FOREIGN KEY (`rubric_id`, `component_id`)
  REFERENCES `rubrics` (`id`, `component_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_10` FOREIGN KEY (`defense_id`, `assignment_id`, `period_id`)
  REFERENCES `defense_sessions` (`id`, `assignment_id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_sheets` ADD CONSTRAINT `fk_score_sheets_01` FOREIGN KEY (`evaluation_id`)
  REFERENCES `evaluation_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_sheets` ADD CONSTRAINT `fk_score_sheets_02` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_sheets` ADD CONSTRAINT `fk_score_sheets_03` FOREIGN KEY (`evaluation_id`, `rubric_id`)
  REFERENCES `evaluation_assignments` (`id`, `rubric_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_01` FOREIGN KEY (`sheet_id`)
  REFERENCES `score_sheets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_02` FOREIGN KEY (`criterion_id`)
  REFERENCES `rubric_criteria` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_03` FOREIGN KEY (`sheet_id`, `rubric_id`)
  REFERENCES `score_sheets` (`id`, `rubric_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_04` FOREIGN KEY (`criterion_id`, `rubric_id`, `max_score`)
  REFERENCES `rubric_criteria` (`id`, `rubric_id`, `max_score`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_versions` ADD CONSTRAINT `fk_result_versions_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_versions` ADD CONSTRAINT `fk_result_versions_02` FOREIGN KEY (`finalized_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_versions` ADD CONSTRAINT `fk_result_versions_03` FOREIGN KEY (`published_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_inputs` ADD CONSTRAINT `fk_result_inputs_01` FOREIGN KEY (`result_id`)
  REFERENCES `result_versions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_inputs` ADD CONSTRAINT `fk_result_inputs_02` FOREIGN KEY (`sheet_id`)
  REFERENCES `score_sheets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_inputs` ADD CONSTRAINT `fk_result_inputs_03` FOREIGN KEY (`component_id`)
  REFERENCES `assessment_components` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_members` ADD CONSTRAINT `fk_result_members_01` FOREIGN KEY (`result_id`)
  REFERENCES `result_versions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_members` ADD CONSTRAINT `fk_result_members_02` FOREIGN KEY (`student_id`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `grade_change_requests` ADD CONSTRAINT `fk_grade_change_requests_01` FOREIGN KEY (`sheet_id`)
  REFERENCES `score_sheets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `grade_change_requests` ADD CONSTRAINT `fk_grade_change_requests_02` FOREIGN KEY (`requested_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `grade_change_requests` ADD CONSTRAINT `fk_grade_change_requests_03` FOREIGN KEY (`reviewed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `announcements` ADD CONSTRAINT `fk_announcements_01` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `announcements` ADD CONSTRAINT `fk_announcements_02` FOREIGN KEY (`file_id`)
  REFERENCES `stored_files` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `announcements` ADD CONSTRAINT `fk_announcements_03` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `audit_logs` ADD CONSTRAINT `fk_audit_logs_01` FOREIGN KEY (`actor_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- PHẦN 3: DỮ LIỆU KHỞI TẠO
-- Không tạo tài khoản có mật khẩu mặc định. Spring tạo password_hash hợp lệ.
INSERT INTO roles (code, name) VALUES
('STUDENT', 'Sinh viên'), ('LECTURER', 'Giảng viên'), ('ADMIN', 'Quản trị hệ thống');

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

CREATE SQL SECURITY INVOKER VIEW v_published_results AS
SELECT rv.id AS result_id, rv.assignment_id, rv.version_no,
       rm.student_id, rm.student_code_snapshot, rm.full_name_snapshot,
       rv.final_score, rv.calculation_rule, rv.published_at
FROM result_versions rv JOIN result_members rm ON rm.result_id = rv.id
WHERE rv.status = 'PUBLISHED';

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
       (SELECT COUNT(*) FROM evaluation_assignments ea
        WHERE ea.assignment_id = v_assignment AND ea.component_id = ac.id AND ea.status = 'ACTIVE') <> ac.expected_evaluators
       OR
       (SELECT COUNT(*) FROM evaluation_assignments ea
        JOIN score_sheets ss ON ss.evaluation_id = ea.id
        JOIN v_score_sheet_totals st ON st.sheet_id = ss.id
        WHERE ea.assignment_id = v_assignment AND ea.component_id = ac.id AND ea.status = 'ACTIVE'
          AND ss.status IN ('SUBMITTED','LOCKED') AND st.total_score IS NOT NULL) <> ac.expected_evaluators
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


-- PHẦN CUỐI: ĐỐI CHIẾU SAU KHI CHẠY
SELECT VERSION() AS mysql_version, DATABASE() AS database_name;
SELECT TABLE_TYPE, COUNT(*) AS object_count
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_TYPE;
SELECT CONSTRAINT_TYPE, COUNT(*) AS constraint_count
FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
GROUP BY CONSTRAINT_TYPE;
SELECT COUNT(*) AS trigger_count FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = DATABASE();
SELECT code, name FROM roles ORDER BY id;
