-- MySQL 8.4 bootstrap step 00. This creates the application database only; it never drops it.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
CREATE DATABASE IF NOT EXISTS `ql_de_tai_sinh_vien`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `ql_de_tai_sinh_vien`;