-- LOCAL/DEMO ONLY. Not imported by the default Docker/VPS configuration.
-- Requires the structured bootstrap and an otherwise empty application database.
-- This guard fails before any INSERT when data is already present, so it cannot
-- silently duplicate unique values or mix demo accounts into a real deployment.
-- Docker local demo: docker compose --env-file docker-compose.demo.env -p qldetai-demo -f docker-compose.yml -f docker-compose.demo.yml up --build -d
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
USE `ql_de_tai_sinh_vien`;

-- A temporary CHECK lets the SQL client stop before inserts without leaving a
-- procedure or other persistent object behind if the preflight fails.
DROP TEMPORARY TABLE IF EXISTS demo_seed_preflight;
CREATE TEMPORARY TABLE demo_seed_preflight (
  id TINYINT NOT NULL PRIMARY KEY,
  is_valid TINYINT NOT NULL,
  CONSTRAINT ck_demo_seed_preflight_valid CHECK (is_valid = 1)
) ENGINE=MEMORY;

SET @demo_seed_preflight_ok := NOT (
  EXISTS (SELECT 1 FROM faculties LIMIT 1)
  OR EXISTS (SELECT 1 FROM departments LIMIT 1)
  OR EXISTS (SELECT 1 FROM users LIMIT 1)
  OR EXISTS (SELECT 1 FROM user_roles LIMIT 1)
  OR EXISTS (SELECT 1 FROM students LIMIT 1)
  OR EXISTS (SELECT 1 FROM lecturers LIMIT 1)
  OR EXISTS (SELECT 1 FROM academic_appointments LIMIT 1)
  OR EXISTS (SELECT 1 FROM semesters LIMIT 1)
  OR EXISTS (SELECT 1 FROM registration_periods LIMIT 1)
  OR EXISTS (SELECT 1 FROM student_groups LIMIT 1)
  OR EXISTS (SELECT 1 FROM topics LIMIT 1)
  OR EXISTS (SELECT 1 FROM topic_supervisors LIMIT 1)
  OR EXISTS (SELECT 1 FROM topic_reviews LIMIT 1)
  OR EXISTS (SELECT 1 FROM group_members LIMIT 1)
  OR EXISTS (SELECT 1 FROM group_invitations LIMIT 1)
  OR EXISTS (SELECT 1 FROM topic_registrations LIMIT 1)
  OR EXISTS (SELECT 1 FROM project_assignments LIMIT 1)
  OR EXISTS (SELECT 1 FROM milestones LIMIT 1)
  OR EXISTS (SELECT 1 FROM stored_files LIMIT 1)
  OR EXISTS (SELECT 1 FROM report_submissions LIMIT 1)
  OR EXISTS (SELECT 1 FROM report_feedback LIMIT 1)
  OR EXISTS (SELECT 1 FROM evaluation_committees LIMIT 1)
  OR EXISTS (SELECT 1 FROM committee_members LIMIT 1)
  OR EXISTS (SELECT 1 FROM defense_sessions LIMIT 1)
  OR EXISTS (SELECT 1 FROM assessment_components LIMIT 1)
  OR EXISTS (SELECT 1 FROM rubrics LIMIT 1)
  OR EXISTS (SELECT 1 FROM rubric_criteria LIMIT 1)
  OR EXISTS (SELECT 1 FROM evaluation_assignments LIMIT 1)
  OR EXISTS (SELECT 1 FROM score_sheets LIMIT 1)
  OR EXISTS (SELECT 1 FROM score_details LIMIT 1)
  OR EXISTS (SELECT 1 FROM result_versions LIMIT 1)
  OR EXISTS (SELECT 1 FROM result_inputs LIMIT 1)
  OR EXISTS (SELECT 1 FROM result_members LIMIT 1)
  OR EXISTS (SELECT 1 FROM grade_change_requests LIMIT 1)
  OR EXISTS (SELECT 1 FROM announcements LIMIT 1)
  OR EXISTS (SELECT 1 FROM notifications LIMIT 1)
  OR EXISTS (SELECT 1 FROM audit_logs LIMIT 1)
  OR EXISTS (SELECT 1 FROM email_notification_settings LIMIT 1)
  OR EXISTS (SELECT 1 FROM email_outbox LIMIT 1)
  OR EXISTS (SELECT 1 FROM email_delivery_logs LIMIT 1)
  OR EXISTS (SELECT 1 FROM email_templates LIMIT 1)
  OR (SELECT COUNT(*) FROM roles WHERE code IN ('STUDENT', 'LECTURER', 'ADMIN')) <> 3
  OR NOT EXISTS (SELECT 1 FROM defense_schedule_guard WHERE id = 1)
);
SELECT IF(@demo_seed_preflight_ok, 'Demo seed preflight passed',
  'Demo seed stopped: database is not empty or system data is missing') AS demo_seed_preflight;
INSERT INTO demo_seed_preflight (id, is_valid) VALUES (1, @demo_seed_preflight_ok);
DROP TEMPORARY TABLE demo_seed_preflight;

START TRANSACTION;

SET @seed_now := UTC_TIMESTAMP(6);
SET @demo_password_hash := '$2a$12$pxiGNs3kmypIx5KSYPzo6.1Kg3TwILoFolYDdAgbz2RlVrVAtNTrS';

INSERT INTO faculties (code, name, is_active)
VALUES ('CNTT', 'Khoa Công nghệ thông tin', 1);
SET @faculty_id := LAST_INSERT_ID();

INSERT INTO departments (faculty_id, code, name, is_active) VALUES
(@faculty_id, 'CNPM', 'Công nghệ phần mềm', 1),
(@faculty_id, 'HTTT', 'Hệ thống thông tin', 1),
(@faculty_id, 'KHMT', 'Khoa học máy tính', 1);
SELECT id INTO @department_software_id FROM departments WHERE faculty_id = @faculty_id AND code = 'CNPM';
SELECT id INTO @department_information_systems_id FROM departments WHERE faculty_id = @faculty_id AND code = 'HTTT';
SELECT id INTO @department_computer_science_id FROM departments WHERE faculty_id = @faculty_id AND code = 'KHMT';

INSERT INTO semesters (academic_year, semester_no, starts_on, ends_on) VALUES
('2025-2026', 2, '2026-01-05', '2026-05-31'),
('2026-2027', 1, '2026-09-01', '2027-01-31');
SELECT id INTO @previous_semester_id FROM semesters WHERE academic_year = '2025-2026' AND semester_no = 2;
SELECT id INTO @current_semester_id FROM semesters WHERE academic_year = '2026-2027' AND semester_no = 1;

INSERT INTO users (email, password_hash, full_name, phone, status, created_at, updated_at, version) VALUES
('admin@demo.local', @demo_password_hash, 'Quản trị viên demo', '0900000001', 'ACTIVE', @seed_now, @seed_now, 0),
('dean@demo.local', @demo_password_hash, 'TS. Nguyễn Minh Đức', '0900000011', 'ACTIVE', @seed_now, @seed_now, 0),
('head-cnpm@demo.local', @demo_password_hash, 'ThS. Trần Thu Hà', '0900000012', 'ACTIVE', @seed_now, @seed_now, 0),
('lecturer01@demo.local', @demo_password_hash, 'ThS. Lê Quốc Bảo', '0900000013', 'ACTIVE', @seed_now, @seed_now, 0),
('lecturer02@demo.local', @demo_password_hash, 'TS. Phạm Ngọc Anh', '0900000014', 'ACTIVE', @seed_now, @seed_now, 0),
('lecturer03@demo.local', @demo_password_hash, 'ThS. Võ Thanh Bình', '0900000015', 'ACTIVE', @seed_now, @seed_now, 0),
('lecturer04@demo.local', @demo_password_hash, 'TS. Đặng Quang Huy', '0900000016', 'ACTIVE', @seed_now, @seed_now, 0),
('student01@demo.local', @demo_password_hash, 'Nguyễn Minh Khang', '0900000101', 'ACTIVE', @seed_now, @seed_now, 0),
('student02@demo.local', @demo_password_hash, 'Trần Gia Huy', '0900000102', 'ACTIVE', @seed_now, @seed_now, 0),
('student03@demo.local', @demo_password_hash, 'Lê Nhật Nam', '0900000103', 'ACTIVE', @seed_now, @seed_now, 0),
('student04@demo.local', @demo_password_hash, 'Phạm Thanh Vy', '0900000104', 'ACTIVE', @seed_now, @seed_now, 0),
('student05@demo.local', @demo_password_hash, 'Võ Hoàng Phúc', '0900000105', 'ACTIVE', @seed_now, @seed_now, 0),
('student06@demo.local', @demo_password_hash, 'Đặng Thu Trang', '0900000106', 'ACTIVE', @seed_now, @seed_now, 0),
('student07@demo.local', @demo_password_hash, 'Bùi Minh Tâm', '0900000107', 'ACTIVE', @seed_now, @seed_now, 0),
('student08@demo.local', @demo_password_hash, 'Đỗ Ngọc Hà', '0900000108', 'ACTIVE', @seed_now, @seed_now, 0),
('student09@demo.local', @demo_password_hash, 'Nguyễn Quốc Việt', '0900000109', 'ACTIVE', @seed_now, @seed_now, 0),
('student10@demo.local', @demo_password_hash, 'Trần Phương Anh', '0900000110', 'ACTIVE', @seed_now, @seed_now, 0);

SELECT id INTO @role_admin_id FROM roles WHERE code = 'ADMIN';
SELECT id INTO @role_lecturer_id FROM roles WHERE code = 'LECTURER';
SELECT id INTO @role_student_id FROM roles WHERE code = 'STUDENT';
SELECT id INTO @admin_user_id FROM users WHERE email = 'admin@demo.local';
SELECT id INTO @dean_user_id FROM users WHERE email = 'dean@demo.local';
SELECT id INTO @head_user_id FROM users WHERE email = 'head-cnpm@demo.local';

INSERT INTO user_roles (user_id, role_id, assigned_at)
VALUES (@admin_user_id, @role_admin_id, @seed_now);
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT id, @role_lecturer_id, @seed_now
FROM users
WHERE email IN ('dean@demo.local', 'head-cnpm@demo.local', 'lecturer01@demo.local', 'lecturer02@demo.local', 'lecturer03@demo.local', 'lecturer04@demo.local');
INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT id, @role_student_id, @seed_now
FROM users
WHERE email IN ('student01@demo.local', 'student02@demo.local', 'student03@demo.local', 'student04@demo.local', 'student05@demo.local', 'student06@demo.local', 'student07@demo.local', 'student08@demo.local', 'student09@demo.local', 'student10@demo.local');

INSERT INTO lecturers (user_id, department_id, lecturer_code, academic_degree, specialization) VALUES
(@dean_user_id, @department_computer_science_id, 'GV001', 'TS.', 'Khoa học dữ liệu'),
(@head_user_id, @department_software_id, 'GV002', 'ThS.', 'Kỹ nghệ phần mềm'),
((SELECT id FROM users WHERE email = 'lecturer01@demo.local'), @department_software_id, 'GV003', 'ThS.', 'Phát triển ứng dụng web'),
((SELECT id FROM users WHERE email = 'lecturer02@demo.local'), @department_information_systems_id, 'GV004', 'TS.', 'Phân tích và thiết kế hệ thống'),
((SELECT id FROM users WHERE email = 'lecturer03@demo.local'), @department_computer_science_id, 'GV005', 'ThS.', 'Trí tuệ nhân tạo'),
((SELECT id FROM users WHERE email = 'lecturer04@demo.local'), @department_software_id, 'GV006', 'TS.', 'An toàn thông tin');
SELECT l.id INTO @dean_lecturer_id FROM lecturers l WHERE l.user_id = @dean_user_id;
SELECT l.id INTO @head_lecturer_id FROM lecturers l WHERE l.user_id = @head_user_id;
SELECT l.id INTO @lecturer_01_id FROM lecturers l JOIN users u ON u.id = l.user_id WHERE u.email = 'lecturer01@demo.local';
SELECT l.id INTO @lecturer_02_id FROM lecturers l JOIN users u ON u.id = l.user_id WHERE u.email = 'lecturer02@demo.local';
SELECT l.id INTO @lecturer_03_id FROM lecturers l JOIN users u ON u.id = l.user_id WHERE u.email = 'lecturer03@demo.local';
SELECT l.id INTO @lecturer_04_id FROM lecturers l JOIN users u ON u.id = l.user_id WHERE u.email = 'lecturer04@demo.local';

INSERT INTO students (user_id, department_id, student_code, class_name, cohort) VALUES
((SELECT id FROM users WHERE email = 'student01@demo.local'), @department_software_id, '22110101', 'DHTI18A1', 'K18'),
((SELECT id FROM users WHERE email = 'student02@demo.local'), @department_software_id, '22110102', 'DHTI18A1', 'K18'),
((SELECT id FROM users WHERE email = 'student03@demo.local'), @department_software_id, '22110103', 'DHTI18A1', 'K18'),
((SELECT id FROM users WHERE email = 'student04@demo.local'), @department_software_id, '22110104', 'DHTI18A1', 'K18'),
((SELECT id FROM users WHERE email = 'student05@demo.local'), @department_software_id, '22110105', 'DHTI18A1', 'K18'),
((SELECT id FROM users WHERE email = 'student06@demo.local'), @department_information_systems_id, '22110206', 'DHTI18A2', 'K18'),
((SELECT id FROM users WHERE email = 'student07@demo.local'), @department_information_systems_id, '22110207', 'DHTI18A2', 'K18'),
((SELECT id FROM users WHERE email = 'student08@demo.local'), @department_information_systems_id, '22110208', 'DHTI18A2', 'K18'),
((SELECT id FROM users WHERE email = 'student09@demo.local'), @department_computer_science_id, '22110309', 'DHTI18A3', 'K18'),
((SELECT id FROM users WHERE email = 'student10@demo.local'), @department_computer_science_id, '22110310', 'DHTI18A3', 'K18');
SELECT id INTO @student_01_id FROM students WHERE student_code = '22110101';
SELECT id INTO @student_02_id FROM students WHERE student_code = '22110102';
SELECT id INTO @student_03_id FROM students WHERE student_code = '22110103';
SELECT id INTO @student_04_id FROM students WHERE student_code = '22110104';
SELECT id INTO @student_05_id FROM students WHERE student_code = '22110105';
SELECT id INTO @student_06_id FROM students WHERE student_code = '22110206';
SELECT id INTO @student_07_id FROM students WHERE student_code = '22110207';
SELECT id INTO @student_08_id FROM students WHERE student_code = '22110208';
SELECT id INTO @student_09_id FROM students WHERE student_code = '22110309';
SELECT id INTO @student_10_id FROM students WHERE student_code = '22110310';

INSERT INTO academic_appointments (lecturer_id, faculty_id, department_id, position_code, starts_at, ends_at, assigned_by) VALUES
(@dean_lecturer_id, @faculty_id, NULL, 'DEAN', DATE_SUB(@seed_now, INTERVAL 365 DAY), NULL, @admin_user_id),
(@head_lecturer_id, @faculty_id, @department_software_id, 'DEPARTMENT_HEAD', DATE_SUB(@seed_now, INTERVAL 365 DAY), NULL, @admin_user_id);

INSERT INTO registration_periods (
  faculty_id, semester_id, name, project_type,
  proposal_start_at, proposal_end_at, registration_start_at, registration_end_at,
  review_deadline_at, defense_date,
  min_group_members, max_group_members, invitation_expiry_hours,
  min_committee_members, max_committee_members,
  status, created_by, created_at, version
) VALUES
(
  @faculty_id, @current_semester_id, 'Đợt đăng ký Đồ án môn học HK1 2026-2027', 'COURSE',
  DATE_SUB(@seed_now, INTERVAL 14 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY), @seed_now, DATE_ADD(@seed_now, INTERVAL 21 DAY),
  NULL, NULL,
  3, 5, 72,
  3, 5,
  'ACTIVE', @admin_user_id, @seed_now, 0
),
(
  @faculty_id, @current_semester_id, 'Đợt Tiểu luận chuyên ngành HK1 2026-2027', 'SPECIALIZED',
  DATE_SUB(@seed_now, INTERVAL 180 DAY), DATE_SUB(@seed_now, INTERVAL 165 DAY), DATE_SUB(@seed_now, INTERVAL 164 DAY), DATE_SUB(@seed_now, INTERVAL 145 DAY),
  DATE_SUB(@seed_now, INTERVAL 125 DAY), NULL,
  3, 5, 72,
  3, 5,
  'CLOSED', @admin_user_id, DATE_SUB(@seed_now, INTERVAL 190 DAY), 0
),
(
  @faculty_id, @previous_semester_id, 'Đợt Khóa luận tốt nghiệp HK2 2025-2026', 'GRADUATION',
  DATE_SUB(@seed_now, INTERVAL 300 DAY), DATE_SUB(@seed_now, INTERVAL 280 DAY), DATE_SUB(@seed_now, INTERVAL 279 DAY), DATE_SUB(@seed_now, INTERVAL 250 DAY),
  DATE_SUB(@seed_now, INTERVAL 220 DAY), DATE_SUB(DATE(@seed_now), INTERVAL 200 DAY),
  3, 5, 72,
  3, 5,
  'CLOSED', @admin_user_id, DATE_SUB(@seed_now, INTERVAL 310 DAY), 0
);
SELECT id INTO @course_period_id FROM registration_periods WHERE name = 'Đợt đăng ký Đồ án môn học HK1 2026-2027';
SELECT id INTO @specialized_period_id FROM registration_periods WHERE name = 'Đợt Tiểu luận chuyên ngành HK1 2026-2027';
SELECT id INTO @graduation_period_id FROM registration_periods WHERE name = 'Đợt Khóa luận tốt nghiệp HK2 2025-2026';

INSERT INTO topics (
  period_id, faculty_id, department_id, code, title, description, objectives, expected_outcomes, prerequisites,
  proposed_by, proposal_source, visibility_type, reserved_group_id, status, published_at, created_at, updated_at, version
) VALUES
(
  @course_period_id, @faculty_id, @department_software_id, 'DAMH-01', 'Xây dựng cổng quản lý đồ án môn học',
  'Xây dựng ứng dụng web quản lý đề tài, nhóm sinh viên và tiến độ thực hiện đồ án môn học.',
  'Áp dụng quy trình phát triển phần mềm và quản lý dữ liệu nghiệp vụ.',
  'Ứng dụng web có phân quyền, tài liệu cài đặt và mã nguồn.', 'Lập trình web, cơ sở dữ liệu.',
  @head_user_id, 'LECTURER', 'OPEN', NULL, 'APPROVED', NULL, DATE_SUB(@seed_now, INTERVAL 12 DAY), DATE_SUB(@seed_now, INTERVAL 10 DAY), 0
),
(
  @course_period_id, @faculty_id, @department_information_systems_id, 'DAMH-02', 'Ứng dụng theo dõi chi tiêu sinh viên',
  'Phát triển ứng dụng ghi nhận thu chi cá nhân và tổng hợp báo cáo theo tháng.',
  'Rèn luyện phân tích yêu cầu và xây dựng giao diện trực quan.',
  'Ứng dụng web hoặc di động, báo cáo chi tiêu và kiểm thử.', 'Lập trình hướng đối tượng.',
  (SELECT id FROM users WHERE email = 'lecturer02@demo.local'), 'LECTURER', 'OPEN', NULL, 'APPROVED', NULL, DATE_SUB(@seed_now, INTERVAL 12 DAY), DATE_SUB(@seed_now, INTERVAL 10 DAY), 0
),
(
  @course_period_id, @faculty_id, @department_computer_science_id, 'DAMH-03', 'Hệ thống nhận diện tin giả tiếng Việt',
  'Xây dựng mô hình phân loại nội dung tin tức tiếng Việt và màn hình tra cứu kết quả.',
  'Làm quen với xử lý dữ liệu và đánh giá mô hình học máy.',
  'Bộ dữ liệu mẫu, mô hình phân loại và báo cáo đánh giá.', 'Python cơ bản, xác suất thống kê.',
  (SELECT id FROM users WHERE email = 'lecturer03@demo.local'), 'LECTURER', 'OPEN', NULL, 'APPROVED', NULL, DATE_SUB(@seed_now, INTERVAL 11 DAY), DATE_SUB(@seed_now, INTERVAL 9 DAY), 0
),
(
  @course_period_id, @faculty_id, @department_software_id, 'DAMH-04', 'Trợ lý học tập cho sinh viên năm nhất',
  'Đề xuất sinh viên đang chờ trưởng bộ môn duyệt trước khi công bố.',
  'Khảo sát nhu cầu và xây dựng nguyên mẫu có thể kiểm thử.',
  'Nguyên mẫu giao diện, báo cáo khảo sát và kế hoạch kiểm thử.', 'Tư duy lập trình cơ bản.',
  (SELECT user_id FROM students WHERE id = @student_06_id), 'STUDENT', 'OPEN', NULL, 'PENDING', NULL, DATE_SUB(@seed_now, INTERVAL 2 DAY), DATE_SUB(@seed_now, INTERVAL 2 DAY), 0
),
(
  @specialized_period_id, @faculty_id, @department_information_systems_id, 'TLCN-01', 'Phân tích dữ liệu tuyển sinh bằng Power BI',
  'Phân tích dữ liệu tuyển sinh và trực quan hóa chỉ số phục vụ quản lý.',
  'Vận dụng quy trình khai phá dữ liệu và lập báo cáo trực quan.',
  'Báo cáo Power BI, tập dữ liệu đã làm sạch và tài liệu hướng dẫn.', 'Cơ sở dữ liệu, thống kê cơ bản.',
  (SELECT id FROM users WHERE email = 'lecturer02@demo.local'), 'LECTURER', 'OPEN', NULL, 'APPROVED', NULL, DATE_SUB(@seed_now, INTERVAL 175 DAY), DATE_SUB(@seed_now, INTERVAL 170 DAY), 0
),
(
  @specialized_period_id, @faculty_id, @department_software_id, 'TLCN-02', 'Kiểm thử tự động cho hệ thống web',
  'Thiết kế bộ kiểm thử tự động và tích hợp vào quy trình CI cơ bản.',
  'Thực hành kiểm thử phần mềm và quản lý chất lượng mã nguồn.',
  'Bộ kiểm thử, pipeline CI và báo cáo chất lượng.', 'Lập trình web, Git.',
  @head_user_id, 'LECTURER', 'OPEN', NULL, 'APPROVED', NULL, DATE_SUB(@seed_now, INTERVAL 175 DAY), DATE_SUB(@seed_now, INTERVAL 170 DAY), 0
),
(
  @graduation_period_id, @faculty_id, @department_software_id, 'KLTN-01', 'Nền tảng hỗ trợ đánh giá đồ án tốt nghiệp',
  'Xây dựng nền tảng phân công phản biện, hội đồng, chấm điểm và công bố kết quả đồ án tốt nghiệp.',
  'Hoàn thiện hệ thống có quy trình nghiệp vụ, phân quyền và khả năng truy vết.',
  'Hệ thống web hoàn chỉnh, tài liệu triển khai và báo cáo kiểm thử.', 'Kiến trúc phần mềm, cơ sở dữ liệu, phát triển web.',
  (SELECT id FROM users WHERE email = 'lecturer01@demo.local'), 'LECTURER', 'OPEN', NULL, 'APPROVED', NULL, DATE_SUB(@seed_now, INTERVAL 290 DAY), DATE_SUB(@seed_now, INTERVAL 285 DAY), 0
),
(
  @graduation_period_id, @faculty_id, @department_computer_science_id, 'KLTN-02', 'Hệ thống giám sát thiết bị IoT trong phòng thí nghiệm',
  'Thu thập dữ liệu cảm biến và cảnh báo khi thiết bị vượt ngưỡng vận hành.',
  'Kết hợp lập trình nhúng, xử lý dữ liệu và giao diện giám sát.',
  'Thiết bị thử nghiệm, dashboard giám sát và báo cáo đánh giá.', 'Lập trình nhúng hoặc mạng máy tính.',
  @dean_user_id, 'LECTURER', 'OPEN', NULL, 'APPROVED', NULL, DATE_SUB(@seed_now, INTERVAL 290 DAY), DATE_SUB(@seed_now, INTERVAL 285 DAY), 0
);

SELECT id INTO @course_topic_01_id FROM topics WHERE period_id = @course_period_id AND code = 'DAMH-01';
SELECT id INTO @course_topic_02_id FROM topics WHERE period_id = @course_period_id AND code = 'DAMH-02';
SELECT id INTO @course_topic_03_id FROM topics WHERE period_id = @course_period_id AND code = 'DAMH-03';
SELECT id INTO @specialized_topic_01_id FROM topics WHERE period_id = @specialized_period_id AND code = 'TLCN-01';
SELECT id INTO @graduation_topic_01_id FROM topics WHERE period_id = @graduation_period_id AND code = 'KLTN-01';

INSERT INTO topic_supervisors (topic_id, lecturer_id, slot_no, assigned_by, assigned_at) VALUES
(@course_topic_01_id, @head_lecturer_id, 1, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 10 DAY)),
(@course_topic_02_id, @lecturer_02_id, 1, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 10 DAY)),
(@course_topic_03_id, @lecturer_03_id, 1, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 9 DAY)),
(@specialized_topic_01_id, @lecturer_02_id, 1, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 170 DAY)),
((SELECT id FROM topics WHERE period_id = @specialized_period_id AND code = 'TLCN-02'), @head_lecturer_id, 1, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 170 DAY)),
(@graduation_topic_01_id, @lecturer_01_id, 1, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 285 DAY)),
((SELECT id FROM topics WHERE period_id = @graduation_period_id AND code = 'KLTN-02'), @dean_lecturer_id, 1, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 285 DAY));

UPDATE topics
SET status = 'PUBLISHED', published_at = DATE_SUB(@seed_now, INTERVAL 8 DAY), updated_at = DATE_SUB(@seed_now, INTERVAL 8 DAY)
WHERE period_id = @course_period_id AND code IN ('DAMH-01', 'DAMH-02', 'DAMH-03');
UPDATE topics
SET status = 'PUBLISHED', published_at = DATE_SUB(@seed_now, INTERVAL 160 DAY), updated_at = DATE_SUB(@seed_now, INTERVAL 160 DAY)
WHERE period_id = @specialized_period_id;
UPDATE topics
SET status = 'PUBLISHED', published_at = DATE_SUB(@seed_now, INTERVAL 270 DAY), updated_at = DATE_SUB(@seed_now, INTERVAL 270 DAY)
WHERE period_id = @graduation_period_id;

INSERT INTO topic_reviews (topic_id, reviewed_by, decision, comment, reviewed_at)
SELECT id, @admin_user_id, 'APPROVED', 'Đề tài đáp ứng phạm vi và yêu cầu của đợt đăng ký.', published_at
FROM topics
WHERE status = 'PUBLISHED';

INSERT INTO student_groups (period_id, name, status, created_by, created_at, version) VALUES
(@course_period_id, 'Nhóm Alpha', 'LOCKED', @student_01_id, DATE_SUB(@seed_now, INTERVAL 7 DAY), 0),
(@course_period_id, 'Nhóm Beta', 'ACTIVE', @student_04_id, DATE_SUB(@seed_now, INTERVAL 2 DAY), 0),
(@course_period_id, 'Nhóm Gamma', 'ACTIVE', @student_07_id, DATE_SUB(@seed_now, INTERVAL 6 DAY), 0),
(@specialized_period_id, 'Nhóm Phân tích dữ liệu', 'CLOSED', @student_04_id, DATE_SUB(@seed_now, INTERVAL 155 DAY), 0),
(@graduation_period_id, 'Nhóm Nền tảng đánh giá', 'CLOSED', @student_08_id, DATE_SUB(@seed_now, INTERVAL 265 DAY), 0);
SELECT id INTO @course_group_alpha_id FROM student_groups WHERE period_id = @course_period_id AND name = 'Nhóm Alpha';
SELECT id INTO @course_group_beta_id FROM student_groups WHERE period_id = @course_period_id AND name = 'Nhóm Beta';
SELECT id INTO @course_group_gamma_id FROM student_groups WHERE period_id = @course_period_id AND name = 'Nhóm Gamma';
SELECT id INTO @specialized_group_id FROM student_groups WHERE period_id = @specialized_period_id AND name = 'Nhóm Phân tích dữ liệu';
SELECT id INTO @graduation_group_id FROM student_groups WHERE period_id = @graduation_period_id AND name = 'Nhóm Nền tảng đánh giá';

INSERT INTO group_members (group_id, period_id, student_id, slot_no, member_role, joined_at, left_at) VALUES
(@course_group_alpha_id, @course_period_id, @student_01_id, 1, 'LEADER', DATE_SUB(@seed_now, INTERVAL 7 DAY), NULL),
(@course_group_alpha_id, @course_period_id, @student_02_id, 2, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 7 DAY), NULL),
(@course_group_alpha_id, @course_period_id, @student_03_id, 3, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 7 DAY), NULL),
(@course_group_beta_id, @course_period_id, @student_04_id, 1, 'LEADER', DATE_SUB(@seed_now, INTERVAL 2 DAY), NULL),
(@course_group_beta_id, @course_period_id, @student_05_id, 2, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 2 DAY), NULL),
(@course_group_beta_id, @course_period_id, @student_06_id, 3, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 2 DAY), NULL),
(@course_group_gamma_id, @course_period_id, @student_07_id, 1, 'LEADER', DATE_SUB(@seed_now, INTERVAL 6 DAY), NULL),
(@course_group_gamma_id, @course_period_id, @student_08_id, 2, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 6 DAY), NULL),
(@course_group_gamma_id, @course_period_id, @student_09_id, 3, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 6 DAY), NULL),
(@specialized_group_id, @specialized_period_id, @student_04_id, 1, 'LEADER', DATE_SUB(@seed_now, INTERVAL 155 DAY), NULL),
(@specialized_group_id, @specialized_period_id, @student_05_id, 2, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 155 DAY), NULL),
(@specialized_group_id, @specialized_period_id, @student_06_id, 3, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 155 DAY), NULL),
(@graduation_group_id, @graduation_period_id, @student_08_id, 1, 'LEADER', DATE_SUB(@seed_now, INTERVAL 265 DAY), NULL),
(@graduation_group_id, @graduation_period_id, @student_09_id, 2, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 265 DAY), NULL),
(@graduation_group_id, @graduation_period_id, @student_10_id, 3, 'MEMBER', DATE_SUB(@seed_now, INTERVAL 265 DAY), NULL);

INSERT INTO group_invitations (group_id, invited_student_id, invited_by, status, created_at, expires_at, responded_at)
VALUES (@course_group_beta_id, @student_10_id, @student_04_id, 'PENDING', DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_ADD(@seed_now, INTERVAL 2 DAY), NULL);

INSERT INTO topic_registrations (
  period_id, group_id, topic_id, registered_by, motivation, status, registered_at, reviewed_by, reviewed_at, decision_reason, version
) VALUES
(
  @course_period_id, @course_group_alpha_id, @course_topic_01_id, @student_01_id,
  'Nhóm đã có kinh nghiệm xây dựng ứng dụng web và muốn giải quyết bài toán quản lý thực tế.',
  'APPROVED', DATE_SUB(@seed_now, INTERVAL 6 DAY), @admin_user_id, DATE_SUB(@seed_now, INTERVAL 5 DAY), NULL, 0
),
(
  @course_period_id, @course_group_gamma_id, @course_topic_02_id, @student_07_id,
  'Nhóm đang hoàn thiện thành viên và đăng ký đề tài theo đúng chuyên môn.',
  'PENDING', DATE_SUB(@seed_now, INTERVAL 1 DAY), NULL, NULL, NULL, 0
),
(
  @specialized_period_id, @specialized_group_id, @specialized_topic_01_id, @student_07_id,
  'Nhóm lựa chọn đề tài phù hợp định hướng hệ thống thông tin.',
  'APPROVED', DATE_SUB(@seed_now, INTERVAL 150 DAY), @admin_user_id, DATE_SUB(@seed_now, INTERVAL 149 DAY), NULL, 0
),
(
  @graduation_period_id, @graduation_group_id, @graduation_topic_01_id, @student_09_id,
  'Nhóm đã hoàn thành các học phần nền tảng và có kế hoạch triển khai đầy đủ.',
  'APPROVED', DATE_SUB(@seed_now, INTERVAL 260 DAY), @admin_user_id, DATE_SUB(@seed_now, INTERVAL 259 DAY), NULL, 0
);
SELECT id INTO @course_alpha_registration_id FROM topic_registrations WHERE group_id = @course_group_alpha_id;
SELECT id INTO @specialized_registration_id FROM topic_registrations WHERE group_id = @specialized_group_id;
SELECT id INTO @graduation_registration_id FROM topic_registrations WHERE group_id = @graduation_group_id;

INSERT INTO project_assignments (registration_id, group_id, topic_id, period_id, assigned_by, assigned_at, status, version) VALUES
(@course_alpha_registration_id, @course_group_alpha_id, @course_topic_01_id, @course_period_id, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 5 DAY), 'IN_PROGRESS', 0),
(@specialized_registration_id, @specialized_group_id, @specialized_topic_01_id, @specialized_period_id, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 149 DAY), 'COMPLETED', 0),
(@graduation_registration_id, @graduation_group_id, @graduation_topic_01_id, @graduation_period_id, @admin_user_id, DATE_SUB(@seed_now, INTERVAL 259 DAY), 'COMPLETED', 0);
SELECT id INTO @course_alpha_assignment_id FROM project_assignments WHERE registration_id = @course_alpha_registration_id;
SELECT id INTO @graduation_assignment_id FROM project_assignments WHERE registration_id = @graduation_registration_id;

INSERT INTO milestones (assignment_id, title, milestone_type, description, due_at, status, created_by, created_at, version) VALUES
(@course_alpha_assignment_id, 'Nộp đề cương', 'PROPOSAL', 'Hoàn thiện phạm vi, kiến trúc và kế hoạch thực hiện đề tài.', DATE_SUB(@seed_now, INTERVAL 1 DAY), 'ACCEPTED', @head_user_id, DATE_SUB(@seed_now, INTERVAL 5 DAY), 0),
(@course_alpha_assignment_id, 'Báo cáo tiến độ lần 1', 'PROGRESS', 'Trình bày chức năng đã hoàn thành, khó khăn và kế hoạch giai đoạn tiếp theo.', DATE_ADD(@seed_now, INTERVAL 7 DAY), 'SUBMITTED', @head_user_id, DATE_SUB(@seed_now, INTERVAL 5 DAY), 0),
(@course_alpha_assignment_id, 'Nộp sản phẩm cuối kỳ', 'FINAL', 'Nộp mã nguồn, tài liệu hướng dẫn và báo cáo tổng kết.', DATE_ADD(@seed_now, INTERVAL 20 DAY), 'OPEN', @head_user_id, DATE_SUB(@seed_now, INTERVAL 5 DAY), 0);

INSERT INTO announcements (faculty_id, title, content, file_id, created_by, status, created_at, published_at) VALUES
(@faculty_id, 'Mở đăng ký Đồ án môn học HK1 2026-2027', 'Sinh viên kiểm tra điều kiện nhóm và đăng ký đề tài trước hạn của đợt.', NULL, @admin_user_id, 'PUBLISHED', DATE_SUB(@seed_now, INTERVAL 1 DAY), DATE_SUB(@seed_now, INTERVAL 1 DAY)),
(@faculty_id, 'Lịch bảo vệ Khóa luận tốt nghiệp HK2 2025-2026', 'Kết quả và lịch bảo vệ của đợt khóa luận đã được công bố.', NULL, @admin_user_id, 'PUBLISHED', DATE_SUB(@seed_now, INTERVAL 200 DAY), DATE_SUB(@seed_now, INTERVAL 200 DAY));

INSERT INTO notifications (user_id, title, content, target_path, created_at, read_at, event_key) VALUES
((SELECT user_id FROM students WHERE id = @student_01_id), 'Đề tài đã được giao', 'Nhóm Alpha đã được giao đề tài DAMH-01. Hãy theo dõi các mốc báo cáo.', '/progress', DATE_SUB(@seed_now, INTERVAL 5 DAY), NULL, 'demo-course-assignment-alpha'),
((SELECT user_id FROM students WHERE id = @student_10_id), 'Lời mời nhóm đang chờ phản hồi', 'Nhóm Beta đã gửi lời mời thành viên và đang chờ phản hồi.', '/groups', DATE_SUB(@seed_now, INTERVAL 1 DAY), NULL, 'demo-course-invitation-beta'),
((SELECT user_id FROM students WHERE id = @student_08_id), 'Kết quả khóa luận đã công bố', 'Kết quả Khóa luận tốt nghiệp của nhóm đã được công bố trên hệ thống.', '/results', DATE_SUB(@seed_now, INTERVAL 195 DAY), DATE_SUB(@seed_now, INTERVAL 194 DAY), 'demo-graduation-result'),
(@head_user_id, 'Có đề tài sinh viên chờ duyệt', 'Đề tài DAMH-04 cần được xem xét trước khi công bố.', '/topics', DATE_SUB(@seed_now, INTERVAL 2 DAY), NULL, 'demo-pending-topic');

INSERT INTO assessment_components (period_id, code, name, component_type, status) VALUES
(@graduation_period_id, 'REVIEW', 'Phản biện độc lập', 'REVIEW', 'DRAFT'),
(@graduation_period_id, 'DEFENSE', 'Hội đồng bảo vệ', 'COMMITTEE', 'DRAFT');
SELECT id INTO @review_component_id FROM assessment_components WHERE period_id = @graduation_period_id AND code = 'REVIEW';
SELECT id INTO @defense_component_id FROM assessment_components WHERE period_id = @graduation_period_id AND code = 'DEFENSE';

INSERT INTO rubrics (component_id, name, version_no, status, created_at) VALUES
(@review_component_id, 'Bộ tiêu chí phản biện khóa luận', 1, 'DRAFT', DATE_SUB(@seed_now, INTERVAL 230 DAY)),
(@defense_component_id, 'Bộ tiêu chí hội đồng bảo vệ', 1, 'DRAFT', DATE_SUB(@seed_now, INTERVAL 230 DAY));
SELECT id INTO @review_rubric_id FROM rubrics WHERE component_id = @review_component_id;
SELECT id INTO @defense_rubric_id FROM rubrics WHERE component_id = @defense_component_id;

INSERT INTO rubric_criteria (rubric_id, name, description, max_score, sort_order) VALUES
(@review_rubric_id, 'Chất lượng nội dung', 'Mức độ hoàn thiện và đúng đắn của nội dung chuyên môn.', 3.00, 1),
(@review_rubric_id, 'Giải pháp kỹ thuật', 'Thiết kế, khả năng triển khai và chất lượng sản phẩm.', 4.00, 2),
(@review_rubric_id, 'Báo cáo và trình bày', 'Tài liệu, cách diễn đạt và minh chứng kết quả.', 3.00, 3),
(@defense_rubric_id, 'Nội dung và kết quả', 'Mức độ đáp ứng mục tiêu đề tài.', 3.00, 1),
(@defense_rubric_id, 'Trình bày và trả lời', 'Kỹ năng thuyết trình, phản biện và trả lời câu hỏi.', 4.00, 2),
(@defense_rubric_id, 'Sản phẩm và tài liệu', 'Khả năng vận hành sản phẩm và chất lượng tài liệu.', 3.00, 3);
UPDATE rubrics SET status = 'LOCKED' WHERE id IN (@review_rubric_id, @defense_rubric_id);
UPDATE assessment_components SET status = 'LOCKED' WHERE id IN (@review_component_id, @defense_component_id);

INSERT INTO evaluation_committees (period_id, faculty_id, name, status, created_by, created_at, version)
VALUES (@graduation_period_id, @faculty_id, 'Hội đồng bảo vệ KLTN số 01', 'DRAFT', @admin_user_id, DATE_SUB(@seed_now, INTERVAL 215 DAY), 0);
SET @committee_id := LAST_INSERT_ID();
INSERT INTO committee_members (committee_id, lecturer_id, slot_no, committee_role) VALUES
(@committee_id, @lecturer_02_id, 1, 'CHAIR'),
(@committee_id, @lecturer_03_id, 2, 'SECRETARY'),
(@committee_id, @lecturer_04_id, 3, 'MEMBER');
UPDATE evaluation_committees SET status = 'COMPLETED' WHERE id = @committee_id;

INSERT INTO defense_sessions (assignment_id, committee_id, period_id, location, starts_at, ends_at, status, version)
VALUES (
  @graduation_assignment_id, @committee_id, @graduation_period_id, 'Phòng B.305',
  DATE_SUB(@seed_now, INTERVAL 200 DAY), DATE_SUB(@seed_now, INTERVAL 200 DAY) + INTERVAL 45 MINUTE, 'COMPLETED', 0
);
SET @defense_session_id := LAST_INSERT_ID();

INSERT INTO evaluation_assignments (
  assignment_id, period_id, component_id, rubric_id, lecturer_id, defense_id, evaluator_role, status, deadline_at
) VALUES
(@graduation_assignment_id, @graduation_period_id, @review_component_id, @review_rubric_id, @dean_lecturer_id, NULL, 'REVIEWER', 'ACTIVE', DATE_SUB(@seed_now, INTERVAL 220 DAY)),
(@graduation_assignment_id, @graduation_period_id, @defense_component_id, @defense_rubric_id, @lecturer_02_id, @defense_session_id, 'COMMITTEE_MEMBER', 'ACTIVE', DATE_SUB(@seed_now, INTERVAL 200 DAY)),
(@graduation_assignment_id, @graduation_period_id, @defense_component_id, @defense_rubric_id, @lecturer_03_id, @defense_session_id, 'COMMITTEE_MEMBER', 'ACTIVE', DATE_SUB(@seed_now, INTERVAL 200 DAY)),
(@graduation_assignment_id, @graduation_period_id, @defense_component_id, @defense_rubric_id, @lecturer_04_id, @defense_session_id, 'COMMITTEE_MEMBER', 'ACTIVE', DATE_SUB(@seed_now, INTERVAL 200 DAY));
SELECT id INTO @review_evaluation_id FROM evaluation_assignments WHERE assignment_id = @graduation_assignment_id AND component_id = @review_component_id;
SELECT id INTO @committee_chair_evaluation_id FROM evaluation_assignments WHERE assignment_id = @graduation_assignment_id AND lecturer_id = @lecturer_02_id;
SELECT id INTO @committee_secretary_evaluation_id FROM evaluation_assignments WHERE assignment_id = @graduation_assignment_id AND lecturer_id = @lecturer_03_id;
SELECT id INTO @committee_member_evaluation_id FROM evaluation_assignments WHERE assignment_id = @graduation_assignment_id AND lecturer_id = @lecturer_04_id;

INSERT INTO score_sheets (evaluation_id, rubric_id, overall_comment, status, submitted_at, version) VALUES
(@review_evaluation_id, @review_rubric_id, 'Đề tài có tính ứng dụng, tài liệu và sản phẩm đáp ứng yêu cầu.', 'DRAFT', NULL, 0),
(@committee_chair_evaluation_id, @defense_rubric_id, 'Nhóm trình bày rõ ràng, trả lời đúng trọng tâm.', 'DRAFT', NULL, 0),
(@committee_secretary_evaluation_id, @defense_rubric_id, 'Sản phẩm hoàn chỉnh và có thể triển khai thử nghiệm.', 'DRAFT', NULL, 0),
(@committee_member_evaluation_id, @defense_rubric_id, 'Nhóm cần bổ sung thêm phụ lục kiểm thử.', 'DRAFT', NULL, 0);
SELECT id INTO @review_sheet_id FROM score_sheets WHERE evaluation_id = @review_evaluation_id;
SELECT id INTO @committee_chair_sheet_id FROM score_sheets WHERE evaluation_id = @committee_chair_evaluation_id;
SELECT id INTO @committee_secretary_sheet_id FROM score_sheets WHERE evaluation_id = @committee_secretary_evaluation_id;
SELECT id INTO @committee_member_sheet_id FROM score_sheets WHERE evaluation_id = @committee_member_evaluation_id;

INSERT INTO score_details (sheet_id, rubric_id, criterion_id, max_score, score, comment) VALUES
(@review_sheet_id, @review_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @review_rubric_id AND sort_order = 1), 3.00, 2.50, 'Nội dung đầy đủ.'),
(@review_sheet_id, @review_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @review_rubric_id AND sort_order = 2), 4.00, 3.50, 'Giải pháp phù hợp.'),
(@review_sheet_id, @review_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @review_rubric_id AND sort_order = 3), 3.00, 3.00, 'Báo cáo rõ ràng.'),
(@committee_chair_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 1), 3.00, 2.70, 'Đáp ứng mục tiêu.'),
(@committee_chair_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 2), 4.00, 3.50, 'Trình bày tốt.'),
(@committee_chair_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 3), 3.00, 2.80, 'Sản phẩm vận hành ổn định.'),
(@committee_secretary_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 1), 3.00, 2.80, 'Kết quả phù hợp.'),
(@committee_secretary_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 2), 4.00, 3.40, 'Trả lời được câu hỏi.'),
(@committee_secretary_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 3), 3.00, 2.70, 'Tài liệu đầy đủ.'),
(@committee_member_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 1), 3.00, 2.60, 'Kết quả đạt yêu cầu.'),
(@committee_member_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 2), 4.00, 3.50, 'Thuyết trình tự tin.'),
(@committee_member_sheet_id, @defense_rubric_id, (SELECT id FROM rubric_criteria WHERE rubric_id = @defense_rubric_id AND sort_order = 3), 3.00, 2.70, 'Nên mở rộng phần kiểm thử.');

UPDATE score_sheets
SET status = 'SUBMITTED', submitted_at = DATE_SUB(@seed_now, INTERVAL 199 DAY)
WHERE id IN (@review_sheet_id, @committee_chair_sheet_id, @committee_secretary_sheet_id, @committee_member_sheet_id);
UPDATE score_sheets
SET status = 'LOCKED'
WHERE id IN (@review_sheet_id, @committee_chair_sheet_id, @committee_secretary_sheet_id, @committee_member_sheet_id);

INSERT INTO result_versions (
  assignment_id, version_no, final_score, calculation_rule, status, finalized_by, finalized_at, published_by, published_at
) VALUES (
  @graduation_assignment_id, 1, 8.95, 'MEAN_COMPONENTS_V1', 'FINALIZED', @admin_user_id, DATE_SUB(@seed_now, INTERVAL 198 DAY), NULL, NULL
);
SET @result_version_id := LAST_INSERT_ID();

INSERT INTO result_inputs (result_id, sheet_id, component_id, sheet_version, score_snapshot, rubric_snapshot) VALUES
(@result_version_id, @review_sheet_id, @review_component_id, 0, 9.00, JSON_OBJECT('rubric', 'Bộ tiêu chí phản biện khóa luận', 'totalScore', 9.00)),
(@result_version_id, @committee_chair_sheet_id, @defense_component_id, 0, 9.00, JSON_OBJECT('rubric', 'Bộ tiêu chí hội đồng bảo vệ', 'totalScore', 9.00)),
(@result_version_id, @committee_secretary_sheet_id, @defense_component_id, 0, 8.90, JSON_OBJECT('rubric', 'Bộ tiêu chí hội đồng bảo vệ', 'totalScore', 8.90)),
(@result_version_id, @committee_member_sheet_id, @defense_component_id, 0, 8.80, JSON_OBJECT('rubric', 'Bộ tiêu chí hội đồng bảo vệ', 'totalScore', 8.80));
INSERT INTO result_members (result_id, student_id, student_code_snapshot, full_name_snapshot) VALUES
(@result_version_id, @student_08_id, '22110208', 'Đỗ Ngọc Hà'),
(@result_version_id, @student_09_id, '22110309', 'Nguyễn Quốc Việt'),
(@result_version_id, @student_10_id, '22110310', 'Trần Phương Anh');
UPDATE result_versions
SET status = 'PUBLISHED', published_by = @admin_user_id, published_at = DATE_SUB(@seed_now, INTERVAL 195 DAY)
WHERE id = @result_version_id;

COMMIT;
