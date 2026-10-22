package com.example.qldetai.feature.evaluation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** One database row serializes scheduling across rooms, faculties and committees. */
@Entity
@Table(name = "defense_schedule_guard")
@Getter
@NoArgsConstructor
public class DefenseScheduleGuard {
    @Id private Integer id;
}
