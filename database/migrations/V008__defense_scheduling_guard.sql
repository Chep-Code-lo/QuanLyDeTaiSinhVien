-- A stable lock target also protects the case where no conflicting session exists yet.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE TABLE IF NOT EXISTS defense_schedule_guard (
    id INT NOT NULL PRIMARY KEY,
    CONSTRAINT ck_defense_schedule_guard_singleton CHECK (id = 1)
) ENGINE=InnoDB;
INSERT INTO defense_schedule_guard (id)
SELECT 1
WHERE NOT EXISTS (SELECT 1 FROM defense_schedule_guard WHERE id = 1);
