-- Required system reference data. This file contains no application account or demo data.
-- INSERT IGNORE keeps an accidental re-run from creating duplicate role/guard records.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
USE `ql_de_tai_sinh_vien`;

INSERT IGNORE INTO roles (code, name) VALUES
('STUDENT', 'Sinh viên'), ('LECTURER', 'Giảng viên'), ('ADMIN', 'Quản trị hệ thống');

INSERT IGNORE INTO defense_schedule_guard (id) VALUES (1);
