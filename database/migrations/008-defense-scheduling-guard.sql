-- A stable lock target also protects the case where no conflicting session exists yet.
CREATE TABLE IF NOT EXISTS defense_schedule_guard (
    id INT NOT NULL PRIMARY KEY,
    CONSTRAINT ck_defense_schedule_guard_singleton CHECK (id = 1)
) ENGINE=InnoDB;
INSERT IGNORE INTO defense_schedule_guard (id) VALUES (1);
